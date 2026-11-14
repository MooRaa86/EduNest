# API Layer — Primary Domain Document

## 1. Overview

33 REST controllers + 2 STOMP message controllers expose the platform. There is no strict API contract layer beyond per-controller conventions: response shapes vary (`SimpleResponse` map-like envelope vs typed DTOs vs `PageResponse<T>`), and authorization is applied at security-URL level + `@PreAuthorize` + service checks (see [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md)).

**Scope:** `controller/**`, `dto/**`, `exception/**` (handler contract), pagination conventions, path conventions.

## 2. Conventions (as built)

| Concern | Convention | Notes |
|---|---|---|
| Identifier Architecture | **Dual Identifier Pattern (EB-109 / D-04)** | **External API Surface:** All request path/query params (`@PathVariable`, `@RequestParam`) and request/response DTOs must strictly accept and serialize opaque `UUID publicId` (string). **Internal Layer:** Database tables, foreign keys, joins, and internal services exclusively use high-performance `Long id` (BIGINT). Controllers/services translate between `publicId` and internal entities via `findByPublicId(...)`. Never expose sequential `Long id`s externally (prevents IDOR guessing and scraping). |
| Versioning | `/api/v1/**` for most modern routes | Legacy/inconsistent prefixes remain: `/login-api`, `/forget-password/**`, `/settings/**`, `/files/**`, `/lectures`, `/student/profile`, `/mentor/profile`, `/student/skills`, `/profile/students`, `/admin/*` (no `/api`), admin badges fully absolute (`/api/admin/badges`, `/api/users/{id}/badges`) — URL-match lists in the security config reflect each |
| Response envelope | `dto/SimpleResponse` (map-ish: `status`, `jwt`, `mentorShip`, …) for many writes/lists; typed response DTOs for newer features; `PageResponse<T>` wraps paged data | No single uniform envelope (API-01) |
| Pagination | `page` + `size` request params → `Pageable` | Common defaults: page 0, size 5/6/10 depending on endpoint (preserved). **Global cap delivered (P4-09/EB-58, 2026-11-14):** every client-supplied `page`/`size` is clamped at construction via `utils/PaginationPolicy` (`page<0`→0; `size<1`→1; `size>50`→50; notification surfaces keep the shipped 100 cap — approved exception A; USR-04 profile previews keep their documented fixed 10s — decision B). Cursored chat history caps `size` only (`page` fixed at 0); `/api/v1/contact/messages/filter` explicitly binds and clamps page/size (default 0/20), preserving Pageable sort parsing. Malformed numeric pagination params (`?size=abc`) → **400 ERR_VALIDATION** (exact param-name set in `GlobalExceptionHandler`; unrelated params keep the sanitized 500). **Contract pinned by P4-09** (`contract/PaginationContractTest`, supersedes the EB-40 500 quirks; plus `MalformedPaginationContractTest` isolation probes): beyond-range page → 200 empty (unchanged). One in-memory paginating aggregator remains ([../mentorship-flow/explore-and-homepage.md](../mentorship-flow/explore-and-homepage.md), P4-10) |
| Validation | Bean Validation on request DTOs (`@Valid`) | Coverage inconsistent; `@Validated` is used **nowhere repo-wide**; login + verify-user lack `@Valid` (SEL-07) |
| Errors | `@RestControllerAdvice GlobalExceptionHandler` | Contract: [error-responses.md](error-responses.md) |
| Serialization | Jackson `fail-on-unknown-properties=true` in common configuration and prod | **EB-63 / P4-14:** dev and prod now reject unknown MVC JSON fields with **400 ERR_UNKNOWN_FIELD** (+ `invalidField`/`allowedFields`); tests: `JacksonProdUnknownFieldContractTest`, `JacksonDevUnknownFieldContractTest`. EB-40 made the prod handler dispatch reachable; previously dev ignored unknown fields. Task/project multipart PATCH preserves its existing sanitized 500 limitation; see [error-responses.md](error-responses.md). |
| Multipart | multipart/form-data endpoints for file uploads/patches | Separate JSON-part + file-part pattern |

**EB-63 compatibility note:** development clients must omit undeclared DTO properties, as production clients already must. Frontend liaison confirmation is pending; the consolidated frontend handoff is deferred. Task/project multipart PATCH unknown fields return the pre-existing sanitized 500, not the MVC unknown-field 400; this is potential follow-up technical debt.

## 3. Documents

- [../architecture/universal-public-id-standard.md](../architecture/universal-public-id-standard.md) — Universal Public UUID Standard (EB-110)
- [endpoints.md](endpoints.md) — complete endpoint inventory (route-verified)
- [error-responses.md](error-responses.md) — exception → status → body contract

## 4. Cross-cutting API risks (IDs in ../ISSUES.md)

- **API-01:** envelope inconsistency (SimpleResponse vs typed vs PageResponse) — client coupling risk.
- **API-02:** unbounded `size`, inconsistent defaults, in-memory pagination.
- **API-03:** email-as-identifier path params (`/api/v1/profile/mentor/{mentorEmail}`, `/settings/*`, admin delete `/{email}`) — fine functionally, leaks enumeration + easy-to-misuse contract.
- **API-04:** mixed password-hash/registration leaks in admin response DTOs (SEL-08) and JWT-in-body (SEL-12).
- **API-05:** Jackson strictness aligned across dev and prod by EB-63; multipart PATCH error handling remains a potential follow-up.

## 5. Related Documentation

[../architecture/MDS.md](../architecture/MDS.md) · [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md) · [../websocket/MDS.md](../websocket/MDS.md) (STOMP surface) · [../ISSUES.md](../ISSUES.md)
