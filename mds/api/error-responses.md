# Error Responses & Status Contract

The full error surface as implemented (`exception/GlobalExceptionHandler`, `@RestControllerAdvice`, verified by reading the whole file), plus the three *other* places that produce error JSON.

## 1. `GlobalExceptionHandler` handler table

Body shape: `ErrorResponse` — a map of `key → message` (via `addError(key,message)`); some handlers return ad-hoc `Map<String,Object>` shapes instead.

| Trigger exception | HTTP | Body |
|---|---|---|
| `EmailAlreadyExistsException` | **409** | `{"Email Error": "<msg>"}` |
| `InvalidOtpException` | **400** | `{"OTP Error": "<msg>"}` |
| `OtpExpiredException` | **410** | `{"OTP Expiration": "<msg>"}` |
| `UserAlreadyVerifiedException` | **400** | `{"Verification Error": "<msg>"}` |
| `UserNotFoundException` (register pkg) | **404** | `{"User Search": "<msg>"}` |
| `RoleNotFoundException` | **500** | `{"System Error": "<msg>"}` |
| `MethodArgumentNotValidException` | **400** | `{<field>: <defaultMessage>, ...}` (one entry per field error + global errors under object name) |
| `HttpMessageNotReadableException` | **400** | enum target: `{"<field>": "Invalid value 'X'. Accepted values are: [...]"}` (empty → "Value cannot be empty..."); otherwise `{"error": "Malformed JSON request or invalid data type"}` |
| `globalLogicEx` (generic business error) | **400** | `{"error": "<msg>"}` |
| `InvalidJwtToken` | **401** | `{"error": "<msg>"}` |
| `EntityNotFoundException` | **404** | `{"error": "entity not found maybe id is invalid bro :)"}` (fixed humorous message) |
| `DataIntegrityViolationException` | **400** | parsed from the MySQL message: Duplicate entry → `{"<key name> already exists."}`; Data too long → `{"<column> exceeds the allowed length."}`; cannot be null → `{"<column> is required."}`; foreign key → `{"error": "Invalid related data provided."}`; fallback `{"error": "Database constraint violation occurred."}` |
| `UsernameNotFoundException` | **404** | `{"error": "<msg>"}` — echoes emails (user enumeration: SEL-03) |
| `BadCredentialsException` | **401** | `{"error": "Invalid password!"}` — distinct from not-found (enumeration) |
| `UnrecognizedPropertyException` | **400** | `{"error":"INVALID_FIELD","message":"Field 'x' is not allowed","invalidField":"x","allowedFields":[...]}` — only possible in **prod** (`fail-on-unknown-properties=true` is set in the prod profile only) |
| **catch-all `Exception`** | **409** | `{"error": exception.getMessage()}` — *every* other error (including 500-class crashes) becomes 409 with a raw internal message (**SEL-14**) |

Notes:
- `AccessDeniedException` (method security) — not listed explicitly → falls to the catch-all as **409** unless the security layer catches it first (prod has the `EduNestAccessDeniedHandler`, which handles *filter-chain* denials, not method-security throws inside controllers; in practice most `AccessDeniedException`s from `@PreAuthorize`/services reach the catch-all → 409 instead of 403 in many paths). Flagged in [../authentication-security/authorization-rules.md](../authentication-security/authorization-rules.md).
- Because the handler is broad, **any unexpected exception** returns HTTP 409 "Conflict" — misleading for clients and monitoring (SEL-14; recommended fix: map to 500 + omit raw message).

## 2. Security-layer JSON shapes (prod)

| Producer | HTTP | Body | Headers |
|---|---|---|---|
| `EduNestAuthenticationEntryPoint` (anonymous blocked) | 401 | `{timestamp, status, error, message, path}` | `EduNest-error-reason: Authentication failed` |
| `EduNestAccessDeniedHandler` (URL-rule denial) | 403 | 403 JSON | `EduNest-denied-reason: Authorization failed` |
| `JwtTokenValidatorFilter` (bad/stale token, DB re-check fail) | 401 | its own JSON (`"Invalid JWT token"` or the deactivation message) | – |

⇒ **Three distinct 401/403 JSON shapes** can reach clients depending on which layer fired (advice vs entry point vs inline filter write) — document for the frontend or unify in stabilization.

## 3. WebSocket errors

- `exception/WebSocketExceptionHandler` — `@MessageExceptionHandler(Exception.class)` → `@SendToUser("/queue/errors")` with the **raw** `ex.getMessage()` as a plain string.
- `ChatMessageService.saveMessage` failure path pushes `/user/{email}/queue/errors` strings itself (room-not-found) and returns `null` to the controller (silent-contract failure mode).

## 4. Contract recommendations (stabilization)

1. Repoint the catch-all to **500** + generic body (log details server-side), keep 409 only for true conflicts.
2. Add explicit handlers for `AccessDeniedException` → 403 and `Forbidden`/`AuthorizationDeniedException`.
3. Unify the 401/403 body shape across entry point/denied handler/filter/advice.
4. Productize the "entity not found bro" message; keep field-level validation map (it's good).
5. Assert `fail-on-unknown-properties` consistency or make the dev behavior explicit in the API docs (silent ignore hides client typos during development).
