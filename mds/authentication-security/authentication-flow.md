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

Controller: `POST /login-api` (`controller/auth/AuthController` lines 34–47, response also exposes JWT in body).

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
2. `POST /forget-password/verify-otp` — requires exactly one `RESET_PENDING` row for the email, matching code and future expiry; it changes that row to `RESET` after verification.
3. `POST /forget-password/reset` — requires exactly one `RESET` row for the email; `RESET_PENDING` and ambiguous `NULL`/`DEFAULT` rows do not qualify. It checks `createdAt.plusHours(1)`, requires an enabled user, BCrypt-encodes the new password and deletes the row.
   - **No secret beyond knowing the email is needed at this step** (no token/code parameter requirement) — the `RESET` row's existence is the proof.
   - **`expiresAt` is not re-checked at this step** (only the 1-hour `createdAt` window).

EB-13 does **not** resolve the caller-bound reset authorization exposure above: EB-14/P1-07 remains open. Registration verification, resend and reset methods use a per-user pessimistic lock; a local H2 lock test demonstrates serialization of that lock, not end-to-end race safety on MySQL or PostgreSQL. Duplicate same-purpose rows fail closed. Expired verification rows are rejected; they are not deleted in the throwing transaction and remain eligible for scheduled cleanup.

**Deployment gate:** compatibility of the persisted `RESET_PENDING` enum value and the locking query with the actual target schema/database is unverified. Security and deployment owners must approve legacy `NULL`/`DEFAULT` reissue, handling existing `RESET` rows, and a mixed-version rollout/rollback procedure before deployment. Older application versions may fail to read the new enum value. No migration or production verification is claimed.

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

- `POST /api/v1/admin/register` (public in both profiles; `AdminController` line 23) → `AdminService.registerAdmin` — throws `globalLogicEx` if any admin exists (first-admin-only), then **echoes the plaintext password in the response** (`AdminService.java:52`).
- `GET /api/v1/admin` — `@PreAuthorize("hasRole('ADMIN')")` **commented out** (`AdminController.java:33`); response maps password hash via `AdminAccResponse` (`AdminService.java:57-66`). URL rule `/api/v1/admin/** → hasRole(ADMIN)` still covers it in prod only.
- Admin password change/email change: `controller/admin/AdminProfileAndSettingsController` (`/admin/profile/*`, OTP flows mirroring user settings).

## 7. Data model (auth-relevant)

`entity/register/OTP` — `otpCode` (plaintext), `expiresAt`, `otpType` (VERIFY/RESET_PENDING/RESET/DEFAULT/DELETE/RESTORE/CHANGE_EMAIL), `pendingEmail`, FK `user_id`, FK `admin_id`, audit columns. Multiple purpose-specific rows can coexist; no database uniqueness constraint is asserted here. Hourly cleanup job.

`entity/users/UserEntity` — `email` (unique), BCrypt `password`, `enabled` (verification/activation), `deleted` (soft delete), `role_id` FK, profile fields.
`entity/users/Admin` — separate table; `email`, `password`, `role_id`, no enabled/deleted columns.
