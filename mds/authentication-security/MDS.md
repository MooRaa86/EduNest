# Authentication & Security — Primary Domain Document

## 1. Overview

Handles the identity lifecycle (registration with email OTP verification, login, JWT issuance/validation, password reset, account delete/deactivate/restore, email change) and all security enforcement (URL rules, JWT filters, method security, object-level ownership checks).

**Business value:** gatekeepers of a paid-content platform — enrollment-gated learning and mentor earnings all rely on correct authentication and authorization.

**Scope:** `controller/auth`, `controller/register`, `controller/account`, `service/auth`, `service/jwt`, `service/register`, `service/security`, `filters`, `config/security`, `config/auth`, `validation`, `annotation`, `exception/authHandling|jwt|registerExceptions`.

Supporting documents:
- [authentication-flow.md](authentication-flow.md) — step-by-step auth/account flows
- [authorization-rules.md](authorization-rules.md) — URL matrix + `@PreAuthorize` inventory + object-level model
- [security-configuration.md](security-configuration.md) — profiles, filter chain, CORS, roles, error handlers
- [security-risks.md](security-risks.md) — dedicated risk register (links into `../ISSUES.md`)
- [legacy-audits/](legacy-audits/) — the team's earlier audit documents (preserved, consolidated here)

## 2. Functional Requirements (as implemented)

| # | Use case | Status |
|---|---|---|
| R1 | Student/mentor registration with email OTP verification (6 digits, 2-min expiry) | Implemented |
| R2 | Login issuing a 7-day HS256 JWT (`Authorization` header **and** response body) | Implemented |
| R3 | Stateless per-request JWT validation + re-check of `enabled`/`deleted` | Implemented |
| R4 | Forgot password: OTP → verify → reset within a 1-hour window | Implemented (with design weaknesses, see risks) |
| R5 | Account delete (soft), deactivate, restore via OTP | Implemented |
| R6 | Change email via OTP sent to the **new** address; change password with old-password check | Implemented |
| R7 | Admin bootstrap: one-time public `POST /api/v1/admin/register` (first admin only, synchronized one-shot guard; credentials never returned; last-admin delete guarded — P1-09/EB-16) | Implemented |
| R8 | Server-side logout/invalidation, refresh tokens | **Not implemented** |
| R9 | Rate limiting / brute-force protection on OTP and login | **Not implemented** |

## 3. Current Implementation (map)

### 3.1 Endpoints

| Endpoint | Method | Purpose | Auth |
|---|---|---|---|
| `/login-api` | POST | Login → JWT in body + `Authorization` header | Public (no `@Valid` on `LoginRequestDto` — `AuthController.java:34`) |
| `/forget-password`, `/forget-password/verify-otp`, `/forget-password/reset` | POST | Reset flow | Public |
| `/api/v1/register/student`, `/api/v1/register/mentor` | POST | Register + send verify OTP | Public |
| `/api/v1/register/verify-user` | POST | Verify OTP → `enabled=true` (DTO missing `@Valid` — `UserManagementController.java:53`) | Public |
| `/api/v1/register/send-otp` | POST | Resend OTP (bare `String email` param, `@Valid @Email` unenforced — no `@Validated`) | Public |
| `/api/v1/register/restore`, `/confirm-restore` | POST | Restore deleted account via OTP | Public |
| `/settings/request-email-change`, `/confirm-email-change`, `/password`, `/delete`, `/confirm-delete`, `/deactivate` | POST/PATCH | Self-service account settings | `@PreAuthorize("isAuthenticated()")` (class) + URL `authenticated()` |
| `/api/v1/admin/register` | POST | First-admin bootstrap (one-shot; `400 ERR_BUSINESS_RULE` once an admin exists) | Public in both profiles; response is identity-only (P1-09/EB-16) |
| `/api/v1/admin` | GET | List admins — **`@PreAuthorize` commented out** (`AdminController.java:33`); response **includes password hash** (`AdminService.java:57-66`) | URL rule ((prod only)) |

### 3.2 Core classes

- `service/jwt/JwtService` — HS256, issuer `"EduNest"`, claims `username` (email), `authorities` (comma-joined roles), `fullName`; secret from `env.getProperty("JWT_SECRET", Constants.JWT_SECRET_DEFAULT_VALUE)` (`getSecretKey`, line ~165); expiry 7 days (`EXPIRATION_TIME_MS`, line 33).
- `filters/JwtTokenValidatorFilter` — prod chain only; parses token, re-checks `UserRepository.isUserEnabledAndNotDeleted` (admins exempt), writes own 401 JSON on failure, skips only `/login-api`.
- `filters/JwtTokenGeneratorFilter` — **dead code**: fires only on `/test-login`, a path that has no controller mapping.
- `service/auth/LoginServiceI` — login process; **hardcodes** `Constants.JWT_SECRET_DEFAULT_VALUE` when parsing role from the generated token (`getJwtRole`, lines 42–47) → broken login role parsing if `JWT_SECRET` env differs.
- `config/auth/EduNestAuthenticationProvider` + `EduNestUserDetailsService` — credential check via `UserRepository.findAuthUser` projection (email, password, enabled, roleName, deleted); disabled/deleted produce distinct messages (**user enumeration**); falls back to `AdminRepository.findByEmail`.
- `service/register/RegisterServiceImpl` + `service/account/SettingsService` + `service/register/EmailService` — OTP flows. OTP data: `entity/register/OTP` (`otpCode` plaintext 6 digits, `expiresAt` = now+2min, `otpType` ∈ `enums/register/OtpType`: `VERIFY, RESET, DEFAULT, DELETE, RESTORE, CHANGE_EMAIL`, `pendingEmail`, FK `user`/`admin`). Cleanup: `OtpCleanupService` hourly.
- `service/security/SecurityService` + `repository/AuthorizationRepository` — object-level checks shared by all domains.

## 4. Architecture & Dependencies

- **Security config is profile-split:** `config/security/ProjectSecurityConfig` (`@Profile("!prod")`, effectively `permitAll()`) and `ProjectSecurityProdconfig` (`@Profile("prod")`, real rules + JWT filters). Both register `AuthenticationManager` with `EduNestAuthenticationProvider`; both disable CSRF; prod is STATELESS and disables httpBasic/formLogin; dev **enables** httpBasic+formLogin.
- **Dependency on env secrets:** DB/mail via env placeholders; JWT secret via `JWT_SECRET` env with committed fallback. `application*.properties` do **not** define `JWT_SECRET` → default runs use the committed secret.
- **Token claims are the authority source** for role checks until expiry (plus the enabled/deleted re-check per request).
- Roles live in `entity/Roles` (`ADMIN`, `MENTOR`, `STUDENT`) seeded by `config/RolesSeeder`.

## 5. Workflows → detailed docs

- Registration/verify/resend: [authentication-flow.md §1](authentication-flow.md)
- Login/JWT generation/validation: [authentication-flow.md §2](authentication-flow.md)
- Forgot password chain: [authentication-flow.md §4](authentication-flow.md)
- Delete/deactivate/restore, email/password change: [authentication-flow.md §5–6](authentication-flow.md)

## 6. Security & Validation

- **Passwords:** BCrypt; min length 8 on register/reset DTOs; **no complexity rules**; `FieldsValueMatch` validates registration confirmation (manual compares elsewhere). The unused legacy `PasswordMatches` path was removed in EB-32.
- **OTP:** 6 digits (`SecureRandom`, `100000..999999`), 2-minute expiry, one active row per user (previous row deleted on issue), plaintext storage, **no attempt counter**, no resend cap, **`otpType` not filtered on account verification** (`RegisterServiceImpl.verifyUser` looks up by email+code only).
- **JWT in URL/WebSocket:** the (dead) handshake interceptor reads a query-param token; channel CONNECT requires an `Authorization` native header — see [../websocket/MDS.md](../websocket/MDS.md).
- **Error-message asymmetries:** login user-not-found vs wrong-password vs disabled reveal account status (enumeration vector, 404/401/400) — `config/auth/EduNestUserDetailsService:59`, handler mapping table in [../api/error-responses.md](../api/error-responses.md).
- Full risk list: [security-risks.md](security-risks.md).

## 7. Error Handling & Edge Cases

- OTP expiry covered (`OtpExpiredException` 410); wrong code (`InvalidOtpException` 400); expired-token-on-public-endpoint quirk: validator filter runs on all routes except `/login-api` → stale `Authorization` breaks otherwise-public calls.
- Reset chain: verify step flips row to `otpType=RESET` and *keeps* it (1-hour usable window, `createdAt`-based, `expiresAt` **not** re-checked) — any further OTP issue for the same user removes the RESET row (single-row-per-user design).
- Deactivated-then-deleted account restore: `confirmRestoreAccount` sets `deleted=false` but leaves `enabled=false` → account remains unusable until... nothing re-enables it (no OTP re-verify path for the restored user; verification OTP issuance is guarded to non-enabled accounts — `restoreAccount` calls `processAndSendOtp` which overwrites the OTP row; outcome requires product decision, tracked as an open question).

## 8. Testing

- No test coverage for any auth flow (the only test is a context-load smoke test). See [../testing/MDS.md](../testing/MDS.md) and [../ISSUES.md](../ISSUES.md) (TST-*).
- Most critical missing tests: JWT validation filter, OTP verify/expiry/type filtering, admin bootstrap single-admin rule, changed-password/old-password check, disabled/deleted token re-check.

## 9. Known Issues & Technical Debt (summary; IDs defined in ../ISSUES.md)

- **SEL-01 (Critical):** hardcoded JWT fallback secret committed (`utils/Constants.java:5`) + `LoginServiceI#getJwtRole` hardcodes it regardless of env (`/login-api` breaks when `JWT_SECRET` is set to anything else).
- **SEL-02 (Critical):** dev security profile = `anyRequest().permitAll()` (every endpoint open when the `prod` profile is absent).
- **SEL-03 (Critical):** no rate limiting on OTP/login; user enumeration via differentiated errors.
- **SEL-04 (High):** password reset step requires no secret itself (DB presence of `RESET` row); `expiresAt` ignored there.
- **SEL-05 (High):** OTP type not checked on verify; OTP plaintext; single shared OTP row allows cross-flow interference.
- **SEL-06 (High):** no logout/refresh/invalidation; 7-day tokens; roles stale until re-login.
- **SEL-07 (Medium):** `@Valid` missing on login and verify-user endpoints; `@Email` constraint on resend-OTP never enforced (no `@Validated`).
- **SEL-08 (Medium):** `AdminController.getAllAdmins` annotation commented out + password hash in response DTO mapping (`AdminService.java:57-66`); register admin echoes plaintext password.
- **SEL-09 (Medium):** WebSocket token (when handshake interceptor is used) via URL query param — log leakage; live code currently uses CONNECT headers.
- **SEL-10 (Medium):** token-in-response-body; `eraseCredentialsAfterAuthentication=false` on the `ProviderManager`.

## 10. Open Questions & Assumptions

- Was the dev-profile `permitAll()` chain intentionally kept for local development, or is it dead weight that should be merged into one hardened config? (Team decision; current state is a deployment foot-gun.)
- Is the 7-day token + no-refresh model deliberate (graduation-project scope) or an interim arrangement awaiting refresh-token support?
- Should password reset verification be bound to a one-time signed token rather than a DB-row presence check? (Recommendation.)
- Restored accounts end `enabled=false` with no re-verification path — confirm intended UX (assume: unintended).

## 11. Related Documentation

- [authentication-flow.md](authentication-flow.md) · [authorization-rules.md](authorization-rules.md) · [security-configuration.md](security-configuration.md) · [security-risks.md](security-risks.md) · [legacy-audits/](legacy-audits/)
- [../websocket/MDS.md](../websocket/MDS.md) (WS auth reality) · [../api/error-responses.md](../api/error-responses.md) (error contract) · [../ISSUES.md](../ISSUES.md) (SEL-* IDs)
