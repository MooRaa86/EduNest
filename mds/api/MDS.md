# API Layer — Primary Domain Document

## 1. Overview

33 REST controllers + 2 STOMP message controllers expose the platform. There is no strict API contract layer beyond per-controller conventions: response shapes vary (`SimpleResponse` map-like envelope vs typed DTOs vs `PageResponse<T>`), and authorization is applied at security-URL level + `@PreAuthorize` + service checks (see [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md)).

**Scope:** `controller/**`, `dto/**`, `exception/**` (handler contract), pagination conventions, path conventions.

## 2. Conventions (as built)

| Concern | Convention | Notes |
|---|---|---|
| Versioning | `/api/v1/**` for most modern routes | Legacy/inconsistent prefixes remain: `/login-api`, `/forget-password/**`, `/settings/**`, `/files/**`, `/lectures`, `/student/profile`, `/mentor/profile`, `/student/skills`, `/profile/students`, `/admin/*` (no `/api`), admin badges fully absolute (`/api/admin/badges`, `/api/users/{id}/badges`) — URL-match lists in the security config reflect each |
| Response envelope | `dto/SimpleResponse` (map-ish: `status`, `jwt`, `mentorShip`, …) for many writes/lists; typed response DTOs for newer features; `PageResponse<T>` wraps paged data | No single uniform envelope (API-01) |
| Pagination | `page` + `size` request params → `Pageable` | Common defaults: page 0, size 5/6/10 depending on endpoint; **no global cap** (`size` unbounded — API-02). One in-memory paginating aggregator exists ([../mentorship-flow/explore-and-homepage.md](../mentorship-flow/explore-and-homepage.md)) |
| Validation | Bean Validation on request DTOs (`@Valid`) | Coverage inconsistent; `@Validated` is used **nowhere repo-wide**; login + verify-user lack `@Valid` (SEL-07) |
| Errors | `@RestControllerAdvice GlobalExceptionHandler` | Contract: [error-responses.md](error-responses.md) |
| Serialization | Jackson; `fail-on-unknown-properties=true` **prod only** → unknown-field 400 there, silent ignore in dev | Contract drift between profiles (API-05) |
| Multipart | multipart/form-data endpoints for file uploads/patches | Separate JSON-part + file-part pattern |

## 3. Documents

- [endpoints.md](endpoints.md) — complete endpoint inventory (route-verified)
- [error-responses.md](error-responses.md) — exception → status → body contract

## 4. Cross-cutting API risks (IDs in ../ISSUES.md)

- **API-01:** envelope inconsistency (SimpleResponse vs typed vs PageResponse) — client coupling risk.
- **API-02:** unbounded `size`, inconsistent defaults, in-memory pagination.
- **API-03:** email-as-identifier path params (`/api/v1/profile/mentor/{mentorEmail}`, `/settings/*`, admin delete `/{email}`) — fine functionally, leaks enumeration + easy-to-misuse contract.
- **API-04:** mixed password-hash/registration leaks in admin response DTOs (SEL-08) and JWT-in-body (SEL-12).
- **API-05:** profile-dependent Jackson strictness (dev vs prod).

## 5. Related Documentation

[../architecture/MDS.md](../architecture/MDS.md) · [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md) · [../websocket/MDS.md](../websocket/MDS.md) (STOMP surface) · [../ISSUES.md](../ISSUES.md)
