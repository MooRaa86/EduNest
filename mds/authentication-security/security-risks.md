# Security Risk Register (auth/security domain focus)

Condensed, prioritized view. Full definitions, evidence and cross-references: [../ISSUES.md](../ISSUES.md). Legacy deep-dives: [legacy-audits/security_audit_report.md](legacy-audits/security_audit_report.md), [legacy-audits/IDOR_Audit_Report.md](legacy-audits/IDOR_Audit_Report.md), [legacy-audits/security-routes-review.md](legacy-audits/security-routes-review.md), [legacy-audits/SECURITY_UPDATES_SUMMARY.md](legacy-audits/SECURITY_UPDATES_SUMMARY.md).

Severity guide: **C**critical / **H**igh / **M**edium / **L**ow. Status: ✅ Confirmed (verified in code) · ⚠️ Suspected (needs investigation) · ❌ Disconfirmed (checked, absent).

## Tier 1 — exploit now, fix before any real deployment

| ID | Title | Status | Anchor |
|---|---|---|---|
| SEL-01 | Hardcoded JWT fallback secret + secret-resolution mismatch (`LoginServiceI#getJwtRole`) | ✅ | `utils/Constants.java:5`; `service/auth/LoginServiceI.java:42-47`; `service/jwt/JwtService.java:165-167` |
| SEL-02 | Dev profile = `anyRequest().permitAll()` (whole API open without `prod`) | ✅ | `config/security/ProjectSecurityConfig.java:53` |
| SEL-03 | No rate limiting on login/OTP endpoints + user enumeration via differentiated errors | ✅ | `EduNestUserDetailsService:59`; `RegisterServiceImpl` flows |
| WS-01 | WebSocket: room membership checks commented out (eavesdrop + write to any room) | ✅ | `config/webSocket/JwtChannelInterceptor` L87–105, 130–140 |
| SEL-04 | Password-reset final step needs no secret (RESET row presence + 1h `createdAt` window; `expiresAt` ignored) | ✅ | `RegisterServiceImpl.resetPassword`:230-245 |
| FILE-01 | `/uploads/**` fully public in prod incl. student submissions; `GET /files?filePath=` has no ownership check | ✅ | `ProjectSecurityProdconfig` L79–97; `FileController#serveFile` L114–136 |

## Tier 2 — fix during stabilization

| ID | Title | Status |
|---|---|---|
| SEL-05 | OTP: no type filtering on verify, plaintext codes, cross-flow row interference (single row per user) | ✅ |
| SEL-06 | No logout/refresh/invalidation; 7-day tokens; roles stale until re-login | ✅ |
| SEL-07 | Missing `@Valid` on `/login-api` + verify-user; `@Email` on resend unenforced (no `@Validated`) | ✅ |
| SEL-08 | Admin list leaks password hash; register echoes plaintext; `@PreAuthorize` commented out | ✅ |
| SEL-09 | AdminBadge endpoints rely on URL rules only (dev-open reads) | ✅ |
| SEL-10 | Notification `send` endpoint lets any authenticated user message anyone (phishing) | ✅ |
| CHAT-02/03 | Conversation history readable + edit IDOR | ✅ |
| SEL-11 | CORS prod: `*` origins + `allowCredentials(true)` | ✅ (benign while header-only auth; dangerous if cookies ever introduced) |
| SEL-12 | Token in login response body (`SimpleResponse.jwt`) + `eraseCredentialsAfterAuthentication=false` | ✅ |
| SEL-13 | WS handshake uses query-param token (dead code today, latent log-leak) | ✅ (latent) |
| SEL-14 | Bulk exception handler returns **409** with raw `getMessage()` (info disclosure + wrong status) — cross-domain but lives here conceptually | ✅ | 
| SEL-15 | Contact-reply HTML injection into email (values not escaped) | ⚠️ |

## Tier 3 — hardening backlog

Unbounded page sizes on chat/notification reads; OWASP-style CSP absent (headers set only on `/files/**` responses via `FileResponseBuilder`); no account lockout; no audit logging of admin actions; secrets-dependent startup (`hmacShaKeyFor` throws for short secrets — no startup validation ⚠️).

## Disconfirmed suspicions (checked by both audits)

- ❌ **No `System.currentTimeMillis()` misuse for chat timestamps** — zero occurrences; DB `@CreationTimestamp` used.
- ❌ **`JwtHandshakeInterceptor` currently active** — it is *never registered* (dead code); handshake is anonymous; auth happens on STOMP CONNECT.

## Preserved context from legacy audits

- `legacy-audits/SECURITY_UPDATES_SUMMARY.md` (May 2026) declared "37/37 controllers protected" — accurate only under prod URL rules; dev-profile exception and the commented-out `getAllAdmins` annotation predate/postdate that claim.
- `legacy-audits/security_audit_report.md` flagged CORS, default secret, 7-day tokens, upload MIME gaps, permitAll uploads/files, missing OTP rate limiting — all still present today (re-verified above).
- `legacy-audits/security-routes-review.md` (Arabic) proposed wildcard-based route groups (MENTOR/STUDENT); not yet implemented — current config still uses per-endpoint lists + `anyRequest().authenticated()`.
