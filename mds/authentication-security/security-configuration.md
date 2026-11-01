# Security Configuration (profiles, filters, CORS, roles)

Verified against `src/main/java/com/example/gradproj/EduNest/config/security/**` and `filters/**`.

> **Implemented 2026-10-28 (P1-02/SEL-02, §1 rewritten):** there is now **one** configuration, `ProjectSecurityConfig`, holding ONE chain bean with two explicit branches (branch selection: `prod` profile → stateless JWT chain; otherwise → permissive chain **only** when the env flag `EDUNEST_UNSAFE_DEV=true` is set explicitly; otherwise the app **refuses to boot**). The former `ProjectSecurityProdconfig` is deleted; `PasswordEncoder`/`AuthenticationManager`/CORS beans are single definitions again. Historical two-class table kept below for audit context.

## 1. Security chain selection (current behavior)

| Mode | Condition | Boot result | Chain |
|---|---|---|---|
| Production | `spring.profiles.active=prod` | ✅ boots | STATELESS JWT chain, authorization matrix, custom 401/403 JSON, httpBasic/formLogin disabled |
| Misconfigured/unset | no `prod` AND no `EDUNEST_UNSAFE_DEV` | ❌ **`IllegalStateException` — refuses to start** | — |
| Explicit dev | `EDUNEST_UNSAFE_DEV=true` (no prod) | ✅ boots **with loud WARN banner** | permissive chain: sessions, httpBasic + formLogin, CSRF off, `.anyRequest().permitAll()` |
| prod + unsafe flag | both set | ✅ boots (flag ignored w/ WARN) | secure JWT chain |

Regression: `SecurityProfileBootMatrixTest` (4 rows). JWT secret ownership: see [`../deployment/environment-configuration.md`](../deployment/environment-configuration.md) (P1-01, `JwtSecretProvider`).

## 1b. Historical: two SecurityFilterChain configurations (superseded)

| Aspect | `ProjectSecurityConfig` (`@Profile("!prod")`) | `ProjectSecurityProdconfig` (`@Profile("prod")`) |
|---|---|---|
| Session policy | default (IF_REQUIRED, sessions allowed) | `STATELESS` |
| CSRF | disabled | disabled |
| httpBasic | **enabled** (`Customizer.withDefaults()`) | disabled |
| formLogin | **enabled** | disabled |
| JWT filters | none | `JwtTokenValidatorFilter` before `BasicAuthenticationFilter`; `JwtTokenGeneratorFilter` after (generator is dead code — see below) |
| Rule behavior | explicit matchers `permitAll()` **and `.anyRequest().permitAll()`** (line 53) | public/admin/authenticated matchers + `.anyRequest().authenticated()` (line 117) |
| CORS | `*` origins/methods/headers, `allowCredentials=false`, exposes `Authorization`, maxAge 3600 | `*` origins/methods/headers, `allowCredentials=false`, exposes `Authorization`, maxAge 3600 (P1-23 / D-23) |
| Exception handling | none custom | `EduNestAuthenticationEntryPoint` (401 JSON + header `EduNest-error-reason`) and `EduNestAccessDeniedHandler` (403 JSON + header `EduNest-denied-reason`) |
| Auth manager | `ProviderManager(EduNestAuthenticationProvider)`, `eraseCredentialsAfterAuthentication=true` | same |
| Method security | `@EnableMethodSecurity` | `@EnableMethodSecurity` |

**Operational consequence:** without the `prod` profile the application refuses to start unless `EDUNEST_UNSAFE_DEV=true` is explicitly set. That opt-in enables the permissive dev chain, except for the upload policy. The Docker image sets `SPRING_PROFILES_ACTIVE=prod` (`Dockerfile` line 13).

## 2. Prod URL rule map (`ProjectSecurityConfig.prodChain`)

### permitAll (Single Source of Truth: `PublicRoutesPolicy.ALL_PUBLIC_PATTERNS`)
```
/swagger-ui/**, /v3/api-docs, /v3/api-docs/**, /swagger-resources/**
/api/auth/**, /api/v1/register/**, /login-api, /forget-password/**
/api/v1/admin/register
/ws/**
/uploads/mentorship-cover-images/**, /uploads/student/**,
/uploads/mentor/**, /uploads/chatroom/**
/api/v1/contact/save-message
/api/v1/student/mentorships/*/overview, /api/v1/student/mentorships/*/reviews
/api/v1/mentorship/explore, /api/v1/mentorship/categories
/api/v1/profile/mentor/**
```
> **P1-03 / EB-10 (SEL-16) Single Source of Truth:** `PublicRoutesPolicy` defines `PUBLIC_API_PATTERNS` concatenated with `UploadPolicy.PUBLIC_UPLOAD_PATTERNS`. Both the Spring Security filter chain (`.requestMatchers(PublicRoutesPolicy.ALL_PUBLIC_PATTERNS.toArray(new String[0])).permitAll()`) and the JWT validation filter reference this single policy.

### hasRole("ADMIN") (lines 99–107)
```
/api/v1/admin/**, /admin/**, /api/admin/**, /api/users/*/badges
/api/v1/contact/all-messages, /api/v1/contact/message/**, /api/v1/contact/messages/**
```

### authenticated (lines 109–114)
```
/settings/**, /api/v1/notifications/**, /student/profile/**, /api/v1/file/**
```

### anyRequest().authenticated()
Everything else — protected from anonymous, but **not role-restricted** (role checks only via `@PreAuthorize` or service checks; see [authorization-rules.md](authorization-rules.md)).

### denyAll
After the public upload patterns, `/uploads/**` denies private, admin and unknown categories to all callers. MVC maps only the public folders. EB-27 adds an authenticated generic `GET /files?filePath=` resolver: public images require a current image reference; private files require one current database reference and typed-resource authorization. Unknown, stale, ambiguous and unauthorized paths return 404. Anonymous requests remain rejected by the production chain. Security/Lead review and frontend coordination are pending.

Notable consequences:
- Swagger UI + OpenAPI docs are public **in prod**.
- Only four explicit upload image folders are public; submissions and attachments require typed ownership-checked routes.
- `/lectures/**`, `/api/v1/task/**`, `/api/v1/project/**`, `/api/v1/quiz/**` etc. are `authenticated()` — anonymity blocked, but any logged-in user passes.

## 3. JWT filter mechanics

- **Secret ownership (`config/security/JwtSecretProvider`, implemented 2026-10-28 — P1-01/SEL-01):** the signing key comes **env-only** (`JWT_SECRET`); the app **fails to boot** when it's missing/blank, <32 bytes, or equal to the retired hardcoded default (digest-matched). One key instance, one parser: `JwtService` delegates to the provider, `LoginServiceI.getJwtRole` delegates to `JwtService.extractAuthorities` — role claims survive secret rotation. No default value exists anywhere in the repo (grep-audited).
- **Validator** (`filters/JwtTokenValidatorFilter`): runs in prod; skips only `/login-api` in `shouldNotFilter`.
  - **Graceful degradation on public routes (EB-10 / P1-03 / SEL-16):** when a stale, expired, or malformed `Authorization` header is passed to a public route (checked via `PublicRoutesPolicy.isPublicRoute(request)`), the filter clears security context and proceeds anonymously (`filterChain.doFilter(request, response)`) instead of aborting with an immediate 401.
  - **Fail-fast on protected routes:** invalid or stale tokens on protected routes continue to be rejected immediately with 401 `ERR_INVALID_TOKEN` (via `ServletErrorWriter`).
  - **Dual-mode preservation:** valid JWTs on public endpoints (e.g., `/api/v1/student/mentorships/*/overview`, `/reviews`) populate `SecurityContextHolder`, preserving authentication context and allowing controllers to resolve personalization/enrollment status.
- **Generator** (`filters/JwtTokenGeneratorFilter`): registered, but `shouldNotFilter` exempts everything except `/test-login` — a path **no controller exposes**. Effectively unreachable; login happens in `service/auth/LoginServiceI` instead.

## 3b. Throttling & account lockout (EB-15 / SEL-03, implemented 2026-10-30 — merge `4529aeb`)

Approved spec (Omar): **8 requests / 1 minute** per bucket (uniform), **5 consecutive failed logins → 5-minute account lock**, **10-minute TTL memory sweep**, **dynamic `Retry-After`** on all 429s.

- **`filters/AuthRateLimitFilter`** — fixed-window counters in memory (`ConcurrentHashMap<key, Window>`), key = `ip | bucket | account-email` (email parsed ONCE from the JSON body, replayed to controllers via `CachedBodyHttpRequestWrapper` — no empty-body breakage). No email in the body → `ip|bucket` fallback. Gated buckets (public credential-producer routes only): `LOGIN` (`/login-api`), `OTP_ISSUE`, `OTP_VERIFY`, `RESTORE_CONFIRM`, `REGISTER`, `ADMIN_REGISTER` — everything else passes untouched. X-Forwarded-For accepted (first hop; single proxy deployments only — document topology before trusting it behind real proxies).
- **`service/auth/LoginLockoutService`** — the account dimension: provider records failures (any canonical-fold failure type) and resets on successful match. The **gate** lives in the filter (`429 ERR_ACCOUNT_LOCKED` + `Retry-After` ≤ 300), checked BEFORE the rate window. In-memory, per-JVM — restarts clear counters; distributed counters are P5-03 (D-27: no Redis/Docker).
- **`config/security/RateLimitProperties`** (`app.rate-limit.*`): `enabled` (default `true`; **false in the test profile** — probes opt back in via `@TestPropertySource`), `default-limit=8`, `default-window-seconds=60`, per-bucket overrides (`app.rate-limit.buckets.<NAME>.limit/window-seconds`), `lockout.threshold=5`, `lockout.wait-seconds=300`, `ttl-seconds=600` (idle-entry sweep, opportunistic every 256 ops).
- **Registration discipline:** the filter is a security-chain member ONLY — `addFilterAfter(authRateLimitFilter, HeaderWriterFilter.class)` in the prod chain (custom filter classes can't anchor `addFilterBefore/After` — no global order slot), plus a disabled `FilterRegistrationBean` so Boot does not double-register the `@Component` filter (double counting would halve the budget), plus a per-request `COUNTED_ATTR` guard as defense in depth.
- **Response contract:** `429 ERR_RATE_LIMITED` (window remainder) / `429 ERR_ACCOUNT_LOCKED` (lock remainder, starts 300) — envelope rows in [../api/error-responses.md](../api/error-responses.md#2-status--errorcode-catalog-rest); FE countdown rules in Jira EF-3.
- **Enumeration node (canonical 401):** `EduNestAuthenticationProvider` folds unknown-email / wrong-password / unverified / deleted into one `BadCredentialsException` → `401 ERR_INVALID_CREDENTIALS` byte-identical bodies (illegal states: no-role misconfig escapes as a runtime exception → sanitized generic `500 ERR_INTERNAL_SYSTEM`, never an enumeration signal). Probes: `RateLimitProbesTest` (R1–R6).

## 3c. Session invalidation model (SEL-06 / D-01 — documented 2026-11-01, Jira EB-34 / P1-27)

**Approved outcome — D-01 (`plan/DECISIONS-REQUIRED.md`, decided 2026-10-31, user-approved): "(a) narrowed — stateless JWT stands for Sprint 1."** No refresh tokens and no server-side revocation model in Sprint 1; logout invalidation is purely **client-side token erasure** (the erasure contract already enforced by EB-11/P1-04). **Refresh tokens + a real session/revocation model are deferred to the Sprint 2 backlog** — at documentation time they are tracked only by the D-01 decision record; **no dedicated Jira issue exists yet**. P1-27/EB-34 therefore **codifies the current state** (per its acceptance criterion "implemented per D-01 or formally carried with decision record"); it does not build refresh. The rejected alternative (b) — a `lastInvalidatedAt` validator check — may be adopted **only via a new D-01 decision line**.

Current behavior, verified against code (`JwtService`, `filters/JwtTokenValidatorFilter`, `service/account/SettingsService`):

| Aspect | As-built (Sprint 1) |
|---|---|
| Token lifetime | **7 days, hardcoded** (`JwtService.EXPIRATION_TIME_MS` = 7×24 h); stateless — no session store, no sliding window, no refresh minting |
| Delivery | Requests carry the **raw JWT in the `Authorization` header** (`Constants.JWT_HEADER`, no `Bearer` prefix); login sets it as the response header, body echo gated by transitional `app.auth.return-token-in-body` (EB-11) |
| Per-request enforcement | Validator re-checks **`enabled` AND not `deleted` from DB on every request** (admins exempt — separate table, no such fields). A state change therefore takes effect on the **next request** (no cache TTL) |

**A token stops working** (the only real invalidation paths):
1. **Natural expiry** — 7 days after minting.
2. **Account deactivation** — `POST /settings/deactivate` sets `enabled=false` → next request → `401 ERR_INVALID_TOKEN` "Invalid token: user is deactivated or deleted, please login again".
3. **Confirmed soft deletion** — `/settings/confirm-delete` sets `deleted=true` (reversible via `/register/restore`). Same 401 as (2); filter-written, unified `ApiError` envelope (EB-12).
4. **Email change (incidental)** — `/settings/confirm-email-change` rewrites the row's email, so outstanding tokens carrying the *old* username claim stop resolving and fail the lookup → same 401. This is a **side effect of the by-email claim lookup, not a designed revocation**.

**A token keeps working** (accepted residual risk until the Sprint 2 model lands — clearly distinguished per D-01):
- **Client logout:** no logout endpoint exists; clients erase the stored token and stop sending it. **This is NOT server-side invalidation —** a preserved or copied token remains fully valid until its 7-day expiry.
- **Password change/reset** (`PATCH /settings/password`, forget-password flow): no account-state check changes, so **all previously minted tokens survive** until expiry.
- **No revocation of any kind:** no session registry, no token blocklist, no `lastInvalidatedAt`.

**Residual risk statement:** a **stolen or hoarded token** (XSS, logs, proxies) remains usable for the remainder of its 7-day window unless the account is deactivated or deleted. Accepted interim mitigations: TLS-only transport, credential-body erasure (EB-11), login throttling/lockout (§3b). This risk is **accepted per D-01** for Sprint 1.

**Reconsideration conditions** (any ⇒ reopen D-01 with a new decision line): the audit or frontend requires real revocation before Sprint 2; cookie- or session-based credentials appear (cf. §5/D-23); removing the transitional body-token property is deemed insufficient to shorten the exposure window.

## 4. Roles & seeding

- `config/RolesSeeder` (CommandLineRunner): inserts `ADMIN`, `MENTOR`, `STUDENT` into `roles` if missing; `System.out.println` on registration.
- Role constants referenced as `SystemUtils.STUDENT/MENTOR/ADMIN` in services; authority strings are `ROLE_*` from JWT claims.

## 5. CORS summary

- Dev: `allowedOrigins(["*"])`, methods/headers `*`, credentials **false**, exposes `Authorization`, `maxAge 3600`.
- Prod: `allowedOrigins(["*"])`, methods/headers `*`, credentials **false** (P1-23 / D-23 / EB-30), exposes `Authorization`, `maxAge 3600`. Auth travels via the `Authorization` bearer header (no cookies/credentials). Wildcard origin is allowed because credentials are not permitted (`allowCredentials=false`). Safety note: if cookie- or session-based cross-origin credentials are introduced in the future, wildcard origin must be dropped and an explicit allowlist defined per D-23.

## 6. Error handlers (security layer)

- `exception/authHandling/EduNestAuthenticationEntryPoint` — 401 JSON `{timestamp,status,error,message,path}` + header `EduNest-error-reason: Authentication failed`.
- `exception/authHandling/EduNestAccessDeniedHandler` — 403 JSON, header `EduNest-denied-reason: Authorization failed`.
- Only wired in the **prod** chain (`ProjectSecurityProdconfig` lines 119–122).
- HTTP-401-vs-403 JSON shape differences vs `GlobalExceptionHandler` are consolidated in [../api/error-responses.md](../api/error-responses.md).

## 7. Custom validators

| Validator | Applied to | Behavior |
|---|---|---|
| `annotation/FieldsValueMatch` + `FieldsValueMatchValidator` | `dto/register/RegisterRequestDto` (`password` vs `confirmPassword`) | BeanWrapper comparison, error at `fieldMatch` |
| None | `ChangePasswordRequest`, reset DTO | manual equals in `SettingsService.changePassword` / DTO constraints |

The unused `PasswordMatches` validator and legacy `dto/auth/RegisterRequest` were removed in EB-32. Password length min 8 on register/reset DTOs; **no complexity rules** anywhere.
