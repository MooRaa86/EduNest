# Domain: Authentication & Security — Implementation Guide

Canonical audit sources: [`mds/authentication-security/MDS.md`](../../../mds/authentication-security/MDS.md), [`authentication-flow.md`](../../../mds/authentication-security/authentication-flow.md), [`security-configuration.md`](../../../mds/authentication-security/security-configuration.md), [`security-risks.md`](../../../mds/authentication-security/security-risks.md), [`authorization-rules.md`](../../../mds/authentication-security/authorization-rules.md) (incl. the **G1–G15** register).

## 1. Purpose & components owned

JWT issuance/validation, security filter chains, public/protected route policy, OTP/reset flows, rate limiting, admin bootstrap, method security, error contract (`mds/api/error-responses.md`), CORS. Components: `service/jwt/JwtService`, `service/auth/*`, `filters/Jwt*Filter`, `config/security/*` (both profile chains), `ProjectSecurityProdconfig`, `GlobalExceptionHandler`.

**Review checklist for any PR touching this domain** (Security Reviewer must check):

- [ ] No secret/credential value introduced (constant, config file, log statement).
- [ ] Chain change enumerates both mechanisms (`SecurityFilterChain` **and** `WebConfig` resource handler) when routes add/remove public surfaces.
- [ ] New/changed endpoints have method security *and* chain rule aligned (annotation alone ≠ sufficient today).
- [ ] Error responses use the unified shape ([P1-05](../../phases/phase-01-security-stabilization/tasks.md#p1-05)).
- [ ] `mvnw test` includes the modified surface in the P2-03 matrix.

## 2. Implementation requirements (per canonical issue)

1. Secret management env-only with fail-fast — issue **SEL-01**; task [P1-01](../../phases/phase-01-security-stabilization/tasks.md#p1-01). *Invariant:* the signing secret exists in exactly one provider class; ROLES parse must not touch the secret.
2. Profile fail-fast: no silent permissive chain — **SEL-02**, [P1-02](../../phases/phase-01-security-stabilization/tasks.md#p1-02). *Invariant:* `permitAll` chain reachable only with explicit `EDUNEST_UNSAFE_DEV=true`.
3. Public-route filter skip — **SEL-16**, [P1-03](../../phases/phase-01-security-stabilization/tasks.md#p1-03); matcher list must be shared with the chain definitions (single source of truth).
4. Token-in-header with transitional body flag; `eraseCredentials` — **SEL-12**, [P1-04](../../phases/phase-01-security-stabilization/tasks.md#p1-04).
5. OTP type isolation + reset one-time binding — **SEL-05/04**, [P1-06](../../phases/phase-01-security-stabilization/tasks.md#p1-06)/[P1-07](../../phases/phase-01-security-stabilization/tasks.md#p1-07). *Invariant:* token lookups/purges are always `otpType`-scoped; reset consumes its row.
6. Throttling/lockout on auth+OTP surfaces — **SEL-03**, [P1-08](../../phases/phase-01-security-stabilization/tasks.md#p1-08); rollout to public routes at [P5-03](../../phases/phase-05-production-readiness/tasks.md#p5-03).
7. Admin bootstrap guard + no cred leakage — **SEL-08/API-04**, [P1-09](../../phases/phase-01-security-stabilization/tasks.md#p1-09); method security sweep incl. room-creation ownership — **SEL-09/TD-07**, [P1-10](../../phases/phase-01-security-stabilization/tasks.md#p1-10).
8. Object-level authorization: G4–G15 canonical closures per `authorization-rules.md` — tasks [P1-14](../../phases/phase-01-security-stabilization/tasks.md#p1-14), [P1-15](../../phases/phase-01-security-stabilization/tasks.md#p1-15), [P1-16](../../phases/phase-01-security-stabilization/tasks.md#p1-16), [P1-17](../../phases/phase-01-security-stabilization/tasks.md#p1-17), [P1-18](../../phases/phase-01-security-stabilization/tasks.md#p1-18), [P1-19](../../phases/phase-01-security-stabilization/tasks.md#p1-19), [P1-20](../../phases/phase-01-security-stabilization/tasks.md#p1-20), [P1-21](../../phases/phase-01-security-stabilization/tasks.md#p1-21) (wired under their own domains in `ISSUE-MAPPING.md`).
9. Notification/mail push endpoints admin-gated — **SEL-10/NOTIF-01/MAIL-01**, [P1-11](../../phases/phase-01-security-stabilization/tasks.md#p1-11).
10. Session invalidation & refresh — **SEL-06/D-01**, conditional [P1-27](../../phases/phase-01-security-stabilization/tasks.md#p1-27) (proceeds on documented default).
11. Bean validation gaps — **SEL-07**, [P1-25](../../phases/phase-01-security-stabilization/tasks.md#p1-25).
12. Contact reply escaping — **SEL-15** after **V-02** ([P0-04](../../phases/phase-00-planning-baseline/tasks.md#p0-04)), fix in [P1-24](../../phases/phase-01-security-stabilization/tasks.md#p1-24).
13. CORS allowlist — **SEL-11/D-23**, [P1-23](../../phases/phase-01-security-stabilization/tasks.md#p1-23).

## 3. Deliberate non-changes

- Path normalization (API-03) rejected here (breaking changes; D-20).
- WS handshake-interceptor token-in-URL stays dormant (SEL-13 deferral; see D-14 trigger).
- Folder *renaming* for uploads policy (TD-09) resolved via policy constants, not renames — [P1-21](../../phases/phase-01-security-stabilization/tasks.md#p1-21).

## 4. Definition of done (domain)

All rows owned by domain `auth` in [`ISSUE-MAPPING.md`](../../ISSUE-MAPPING.md) Verified/Deferred-by-decision; `authorization-rules.md` matrix re-verifies as green in the P2-03 run; review checklist above is checked on every merged PR of this domain.
