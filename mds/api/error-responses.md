# Error Responses & Status Contract

**EB-12 / P1-05 (2026-10-29):** ONE envelope everywhere — every client-facing
error from every producer (REST advice, security entry point, access-denied
handler, JWT filter) is the exact same shape. `errorCode` is the machine
contract (FE branches on it); `message` is guaranteed clean and FE-renderable.
Probes: `security/probes/ErrorShapeProbeTest.java` (E1–E6, zero-leak asserted).

## 1. The envelope

```json
{
  "timestamp": "2026-10-29T19:30:00",
  "status": 403,
  "error": "Forbidden",
  "errorCode": "ERR_FORBIDDEN",
  "message": "You do not have permission to access this resource.",
  "path": "/api/v1/contact/all-messages",
  "refId": "b3a1f00c"
}
```

- `errorCode` — stable machine code (see `exception/ApiErrorCode`); never changes wording.
- `message` — clean human text; renders directly in the UI; contains **no** stack traces, SQL, class names, or framework echoes.
- `refId` — present ONLY on sanitized 500s; correlates with the backend log line (`Unhandled exception [refId=…]`).
- `fields` — present on `ERR_VALIDATION` (D-26 permanent contract) and `ERR_UNKNOWN_FIELD`/enum hints: a `key → message` map.

Produced by exactly one serializer (`exception/ServletErrorWriter`) for the
servlet layer and one envelope class (`exception/ApiError`) for the advice —
five producers, no drift (E6 pins this).

## 2. Status + errorCode catalog (REST)

| Failure | HTTP | errorCode | message (clean) |
|---|---|---|---|
| Unhandled exception (catch-all) | **500** (was 409+leak) | `ERR_INTERNAL_SYSTEM` | `An unexpected system error occurred. Please try again.` (+ refId) |
| `@PreAuthorize`/service `AccessDeniedException` | **403** (was 409) | `ERR_FORBIDDEN` | `You do not have permission to access this resource.` |
| Filter-chain denial (URL rules) | 403 | `ERR_FORBIDDEN` | same as above |
| Anonymous request to protected route | 401 | `ERR_UNAUTHENTICATED` | `Authentication required. Please log in.` |
| Bad/deactivated-token JWT (filter + advice) | 401 | `ERR_INVALID_TOKEN` | `Invalid token: user is deactivated or deleted, please login again` / `Invalid JWT token, please login again` |
| Login bad password | 401 | `ERR_INVALID_CREDENTIALS` | `Invalid email or password.` |
| Business rule (`globalLogicEx`) | 400 | `ERR_BUSINESS_RULE` | throw-site text (audited, user-facing) |
| Bean validation (`MethodArgumentNotValid`) | 400 | `ERR_VALIDATION` (+ `fields` map) | `Please check the submitted fields.` |
| Malformed JSON / type conversion | 400 | `ERR_MALFORMED_BODY` | `Malformed JSON request or invalid data type.` (enum hints under `fields`) |
| Unknown JSON field (prod) | 400 | `ERR_UNKNOWN_FIELD` | `Field 'x' is not allowed.` (+ `fields`: invalidField, allowedFields) |
| `EmailAlreadyExists` | 409 | `ERR_EMAIL_IN_USE` | throw-site / `Email is already registered.` |
| `InvalidOtp` / `OtpExpired` / `UserAlreadyVerified` | 400 / 410 / 400 | `ERR_INVALID_OTP` / `ERR_OTP_EXPIRED` / `ERR_ALREADY_VERIFIED` | friendly, fixed fallbacks |
| `UserNotFound` / `UsernameNotFound` | 404 | `ERR_USER_NOT_FOUND` | fixed `User not found.` (email echo retired; enumeration wording unifies with EB-15) |
| `EntityNotFound` | 404 | `ERR_RESOURCE_NOT_FOUND` | `The requested item was not found.` (bro joke retired) |
| `DataIntegrityViolation` | 400 | `ERR_CONSTRAINT_VIOLATION` | category text only (`This value is already in use.`, `One of the submitted values is too long.`, `Some required information is missing.`, `Invalid related data provided.`) — **raw MySQL index/column identifiers stripped** |
| `RoleNotFound` (server misconfig) | 500 | `ERR_INTERNAL_SYSTEM` | generic sanitized (role name never sent) |

Login enumeration neutrality (identical bodies for unknown-user vs bad-password)
is **EB-15's** scope (`SEL-03`) — this PR only fixed the shape + fixed texts.

## 3. Producers (all one shape)

| Producer | HTTP | errorCode |
|---|---|---|
| `GlobalExceptionHandler` (`@RestControllerAdvice`) | per catalog | per catalog |
| `EduNestAuthenticationEntryPoint` | 401 | `ERR_UNAUTHENTICATED` |
| `EduNestAccessDeniedHandler` | 403 | `ERR_FORBIDDEN` (old `EduNest-denied-reason` header removed) |
| `JwtTokenValidatorFilter` (inline writes) | 401 | `ERR_INVALID_TOKEN` |

## 4. WebSocket

- `/user/queue/errors` frames remain plain strings (documented WS contract):
  `globalLogicEx` → authored business text; anything unexpected → the same
  generic sanitized sentence as REST 500. No refId over WS (log line carries it).
- Room-not-found push from `ChatMessageService` unchanged (tracked in
  ISSUE-MAPPING chat rows / P4-06).

## 5. Anti-leak policy (enforced by probes E1/E3 + assertNoInternalLeak)

Blocked from bodies forever: exception class names, `java.*`/`org.*` internals,
Hibernate/SQL text, stack markers, framework echo messages (`Full authentication
is required…`). Details go to logs with refId correlation.
