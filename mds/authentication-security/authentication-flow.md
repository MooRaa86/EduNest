# Authentication & Account Flows

Source root: `src/main/java/com/example/gradproj/EduNest/`. EB-13 changes below are implemented and locally tested on the task branch, awaiting review and verification; they are not a deployment claim. Other sections retain audit-era observations.

## 1. Registration + OTP verification

Entry points: `POST /api/v1/register/student`, `POST /api/v1/register/mentor` (`controller/register/UserManagementController`).

1. `RegisterServiceImpl.registerStudent/registerMentor` (lines 84 / 113):
   - `existsByEmail` → `EmailAlreadyExistsException` (409) — **leaks which emails are registered**.
   - Role copied from `roleRepository.findByName(STUDENT/MENTOR)`; BCrypt-encode password; entity saved with `enabled=false`, `deleted=false` (`entity/users/UserEntity` flags).
2. OTP issuance — `processAndSendOtp` (line 51):
   - Deletes existing `VERIFY` rows for the user only; other purposes remain intact.
   - Generates 6 digits via `SecureRandom` (`100000 + nextInt(900000)`) in `generateOTP` (line 46).
   - Saves a `VERIFY` OTP with `expiresAt = now + 2 minutes` (`expiryTime = 2`).
   - Sends HTML email (`templates/emails/otp-template.html`, placeholders `{{OTP_CODE}}, {{USER_NAME}}, {{EXPIRY_MINUTES}}`) via `EmailService.sendEmail` (`@Async`; SMTP failure raises inside async task).
3. Verification — `POST /api/v1/register/verify-user` → `RegisterServiceImpl.verifyUser` (line 160):
   - Already-enabled → `UserAlreadyVerifiedException` (400).
   - Lookup is scoped to email and `VERIFY`; exactly one row must exist and its code must match. Ambiguous `NULL`/`DEFAULT` rows are not accepted.
   - Missing or non-future `expiresAt` → `OtpExpiredException` (410); the scheduled cleanup handles expired rows.
   - Sets `enabled=true`, deletes the OTP row.
4. Resend — `POST /api/v1/register/send-otp` → `generateAndSendOtp` (line 183): rejects already-enabled users; issue #2 rules otherwise. **No resend cap / rate limit.**
5. Hourly purge of expired rows: `service/register/OtpCleanupService.removeExpiredOtps` (`@Scheduled(fixedRate=3600000)`).

## 2. Login (prod profile)

Controller: `POST /login-api` returns the raw JWT in the `Authorization` response header. The `apiResponse.jwt` body field is controlled by `app.auth.return-token-in-body` (`APP_AUTH_RETURN_TOKEN_IN_BODY`); it defaults to `true` for existing frontend consumers and can be set to `false` after migration. Frontend confirmation of migration remains outstanding.

1. `LoginServiceI.loginProcess` (line 27) builds `UsernamePasswordAuthenticationToken(email, password)`.
2. `EduNestAuthenticationProvider.authenticate` (`config/auth/`) → `EduNestUserDetailsService.loadUserByUsername`:
   - `UserRepository.findAuthUser` projection (id, email, password, enabled, roleName, deleted).
   - No role → ` globalLogicEx`; disabled → "This account is not verified, request a new otp..."; deleted → "This User is deleted! Contact us.."; password mismatch → `BadCredentialsException("Invalid password!")`.
   - Admin fallback: `AdminRepository.findByEmail` (admins have no enabled/deleted columns).
3. JWT — `JwtService.generateToken` (line 41):
   - HS256, issuer `"EduNest"`, subject `"JWT Token"`, `exp = now + 7 days` (`EXPIRATION_TIME_MS`, line 33).
   - Claims: `username` (email), `authorities` (`ROLE_MENTOR` etc., comma-joined), `fullName` (`UserRepository.findFullNameByEmail` or Admin first+last).
   - Secret: `getSecretKey` (line 165) uses `env.getProperty(Constants.JWT_SECRET_KEY, Constants.JWT_SECRET_DEFAULT_VALUE)` → **committed default `jxgEQeXKcPq8VdbyYFNkANomdQ53YUn4` if env unset**.
4. Role in the response is parsed by `LoginServiceI.getJwtRole` (line 41) — **hardcodes the default secret instead of env resolution** → when `JWT_SECRET` env differs, login fails here despite correct issuance/validation.
5. Response: JWT in `SimpleResponse.jwt` **and** `Authorization` header. No cookie, **no refresh token** anywhere in the repo.

## 3. Per-request JWT validation (prod only)

`filters/JwtTokenValidatorFilter` (registered before `BasicAuthenticationFilter`):

- Header `Authorization` (`Constants.JWT_HEADER`); absent → pass through (anonymous).
- `shouldNotFilter` exempts **only** `/login-api` (line 56) → carrying an expired/invalid token to any other public endpoint (register, verify, forget-password...) yields 401 JSON from the filter itself rather than anonymous access.
- Validate: signature+expiry (`JwtService.validateToken`), then `UserRepository.isUserEnabledAndNotDeleted(username)` (admins exempt). Disabled/deleted → 401 "Invalid token: user is deactivated or deleted, please login again".
- Success → `SecurityContextHolder` gets `UsernamePasswordAuthenticationToken(username, null, commaSeparatedStringToAuthorityList(claims.authorities))`.

Implications: role changes (e.g. seeding corrections) do **not** propagate until re-login; a 401 on public endpoints with stale tokens is an API-contract surprise for clients.

## 4. Forgot password

Files: `controller/auth/AuthController`, `RegisterServiceImpl.forgetPassword/verifyForgetPasswordOtp/resetPassword` (lines 197/210/229), templates `forget-password-otp-template.html`.

1. `POST /forget-password` — enabled user required; removes that user's `RESET_PENDING` and `RESET` rows and issues a new `RESET_PENDING` OTP. Other purposes are preserved.
2. `POST /forget-password/verify-otp` takes `{"email":"...","otp":"123456"}` and requires exactly one unexpired matching `RESET_PENDING` row. On success it returns `{"apiResponse":{"message":"OTP verified successfully.","resetToken":"<one-time-token>"}}` with `Cache-Control: no-store`. The 32-byte random token is returned only to the verifier; the `RESET` row stores its `v1:`-prefixed SHA-256 hex digest, never the token.
3. `POST /forget-password/reset` takes `{"email":"...","resetToken":"<one-time-token>","newPassword":"..."}`. It requires an enabled user and exactly one `RESET` row with a matching digest and future `expiresAt`. The password change and row deletion occur in one transaction under the existing per-user lock. Missing, legacy, expired, invalid and consumed credentials fail closed.

The frontend must hold the token only in memory, submit it over HTTPS in the reset request body, and discard it after use; never put it in URLs, logs or persistent browser storage. A lost response requires a new OTP request. The reset-token lifetime is independently configurable as `app.auth.reset-token-ttl` (Spring duration, e.g. `PT5M`); the five-minute default was approved by the Team Lead and merged with [PR #30](https://github.com/MooRaa86/EduNest/pull/30) (merge commit `52452a0`, 2026-10-29; Linux/JDK 21 CI 187/187). Issuing a new OTP invalidates the old reset credential. EB-15 remains responsible for OTP throttling and enumeration protection.

**Deployment gates:** local H2 metadata/test evidence does not establish the real MySQL or Supabase PostgreSQL `otp.otp_code` capacity (at least 67 characters), enum constraints, or lock/transaction behavior. Verify the actual schema and concurrency against the target database before deployment. Legacy `RESET` rows cannot be used and require a new OTP. Old backend instances accept email-only reset requests, so do not expose mixed old/new instances during rollout or rollback; disable the reset flow or coordinate an atomic traffic cutover. No migration or production verification is claimed. JWT/session revocation is separate work.

## 5. Account delete / deactivate / restore

`service/account/SettingsService` (class-level `isAuthenticated()`).

| Flow | Endpoint | Mechanics | Template |
|---|---|---|---|
| Delete | `POST /settings/delete` → `POST /settings/confirm-delete?otp=` | issue `OtpType.DELETE` (2 min) → verify code+expiry → `deleted=true` | `delete-account.html` |
| Deactivate | `POST /settings/deactivate?password=` | BCrypt check → `enabled=false` + `SecurityContextHolder.clearContext()` (no-op in stateless chain) | – |
| Restore | `POST /api/v1/register/restore` → `/confirm-restore` (`RegisterServiceImpl` lines 259/297) | requires `deleted=true`; issue `OtpType.RESTORE` (2 min) → verify → `deleted=false`. **Does not set `enabled`** — restored accounts remain disabled with no re-verification journey | `restore-account.html` |
| Change email | `POST /settings/request-email-change` → `PATCH /settings/confirm-email-change?otpCode=` | issue `OtpType.CHANGE_EMAIL` with `pendingEmail` (sent to the **new** address); reject same/taken email; verify → overwrite `user.email`, delete rows, clear context. Old JWT's `username` stops matching a user → 401 on next request | `change-email.html` |
| Change password | `PATCH /settings/password` | old password BCrypt match + manual new/confirm equality; re-encode | – |

Soft-delete flag lives on `UserEntity.deleted` (default false, lines 61–63). Admins have neither flag (`entity/users/Admin`).

## 6. Admin bootstrap

| 🔓 POST `/api/v1/admin/register` (public in both profiles; `AdminController` line 23) → `AdminService.registerAdmin` — synchronized one-shot guard (first admin only), then returns **identity only** — the plaintext is encoded and persisted, never echoed (fixed by P1-09/EB-16).
- `GET /api/v1/admin` — `@PreAuthorize("hasRole('ADMIN')")` **commented out** (`AdminController.java:33`); response is **identity only** (`AdminAccResponse` no longer has `password` field, fixed by P1-09/EB-16). URL rule `/api/v1/admin/** → hasRole(ADMIN)` still covers it in prod only.
- Admin password change/email change: `controller/admin/AdminProfileAndSettingsController` (`/admin/profile/*`, OTP flows mirroring user settings).

## 7. Data model (auth-relevant)

`entity/register/OTP` — `otpCode` (plaintext), `expiresAt`, `otpType` (VERIFY/RESET_PENDING/RESET/DEFAULT/DELETE/RESTORE/CHANGE_EMAIL), `pendingEmail`, FK `user_id`, FK `admin_id`, audit columns. Multiple purpose-specific rows can coexist; no database uniqueness constraint is asserted here. Hourly cleanup job.

`entity/users/UserEntity` — `email` (unique), BCrypt `password`, `enabled` (verification/activation), `deleted` (soft delete), `role_id` FK, profile fields.
`entity/users/Admin` — separate table; `email`, `password`, `role_id`, no enabled/deleted columns.
