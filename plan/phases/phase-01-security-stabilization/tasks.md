# Phase 01 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md). The `<a id="pN-nn">` anchors below each heading are the link targets used by phase/domain docs (`tasks.md#p1-nn`).
<a id="p1-01"></a>
### P1-01 — JWT secret: env-only + role-parse fix

**Priority:** P0 — with the hardcoded fallback (`jxgEQe…`, `JwtService:165-167`) any deployment is token-forgeable; this is today's single worst asset.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-01 (+DEP-05)   **Domain:** authentication-security
**Components:** `utils/Constants`, `service/jwt/JwtService`, `service/auth/LoginServiceI#getJwtRole`

**Context:** The signing key silently falls back to a public constant, and `LoginServiceI:41-47` parses roles by string-replacing the *secret itself* — a rotated secret breaks role claims silently. Both defects share one root: secret handling spread across three classes.
**Evidence:** [`mds/authentication-security/security-risks.md` §JWT](../../../mds/authentication-security/security-risks.md); ISSUES SEL-01, DEP-05.
**Expected outcome:** app **fails to boot** when `JWT_SECRET` is absent or <32 bytes; role parsing works with any secret value; the fallback constant no longer exists in the repo.
**Approach:** delete the default from `Constants`; add a validated `@ConfigurationProperties` (or `@Value` + `@PostConstruct`) secret provider with length check; make `LoginServiceI.getJwtRole` delegate to `JwtService`'s parser; keep claim-building logic untouched.
**FE / API impact:** none (token format unchanged).  **Environment access:** none.  **Decisions:** none (feeds D-22/P6-03 ordering — see R1).
**Depends on:** —   **Blocks:** P6-03 (real secret provisioning must come **after** this merge).
**Tests / regressions:** unit: boot fails w/o env; role parse with rotated secret; integration: login → token → authenticated call.
**Acceptance criteria:**
- [ ] `grep -r "jxgEQe"` returns nothing (src + tests).
- [ ] Boot without `JWT_SECRET` exits with a clear startup error (log capture).
- [ ] Role claim parse test passes with two different secrets.
**DoD:** per [TEAM-WORKFLOW §5](../../TEAM-WORKFLOW.md#5-definition-of-ready--definition-of-done).
**Docs to update:** `mds/authentication-security/security-configuration.md`; `ISSUE-MAPPING.md` SEL-01 row.
**Status:** ~~Planned~~ **Verified (2026-10-28)** · **Evidence:** PR `feat/p1-01-jwt-secret-env-only` merged to main; `mvnw test` 7/7 (`JwtSecretProviderTest`, `JwtRoleRotationTest`); live boot: refused missing secret, refused short secret, green with valid secret; grep-audit clean.

<a id="p1-02"></a>
### P1-02 — Fail-fast security profile (no silent permissive chain)

**Priority:** P0 — today, booting without the `prod` profile yields the sessionful permitAll chain (`ProjectSecurityConfig.java:53`): an easier production mistake than a missing secret.  **Size:** M
**Owner role:** Backend Developer (Security-config steward)   **Reviewers:** Security Reviewer + Eng Lead
**Issues:** SEL-02 (+ARCH-001)   **Domain:** authentication-security
**Components:** `ProjectSecurityConfig`, `ProjectSecurityProdconfig`, startup validator, `application*.properties`

**Context:** Two independent `SecurityFilterChain` beans; which one wins is decided by which profile is active — and the wrong guess boots happily. The dev chain also enables CSRF-off form login/open registration, which must never be reachable by accident.
**Evidence:** ISSUES SEL-02; [`mds/authentication-security/security-configuration.md`](../../../mds/authentication-security/security-configuration.md); P0-09 rehearsal provides "before" evidence.
**Expected outcome:** default boot (no profile) **fails fast** with an explicit error; the permissive chain is only constructible when `EDUNEST_UNSAFE_DEV=true` is explicitly set; `prod` chain unchanged in behavior.
**Approach:** collapse to one `@Bean` that selects chain by profile/env; add a startup `ApplicationRunner`/`EnvironmentPostProcessor` validating the combination; loud WARN banner when unsafe mode active; document local-dev usage in `mds/authentication-security/security-configuration.md` §profiles.
**FE / API impact:** none for correct deployments; misconfigured ones now refuse to start (intended).  **Environment access:** none.  **Decisions:** none (technical; default documented).
**Depends on:** —   **Blocks:** P2-03 boot-matrix tests; P6-09 config matrix.
**Tests / regressions:** boot matrix test: prod ✅ boots; none ❌ fails; `EDUNEST_UNSAFE_DEV=true` ✅ boots with WARN.
**Acceptance criteria:**
- [x] Boot test proves all three states (evidence: test log).
- [x] `application-prod.properties` reviewed: no permitAll chain co-existence possible (single chain bean).
**Docs to update:** security-configuration.md; ISSUE-MAPPING SEL-02/ARCH-001 rows.
**Status:** ✅ **Verified** (2026-10-28) — single `ProjectSecurityConfig` with one chain bean + two explicit branches; boot guard in the config constructor; `EDUNEST_UNSAFE_DEV=true` escape hatch with WARN banner (prod wins conflicts). `ProjectSecurityProdconfig` deleted; `PasswordEncoder`/`AuthenticationManager`/CORS beans de-duplicated. Boot matrix `SecurityProfileBootMatrixTest` 4/4 (covers prod ✅ / none ❌ refusal / unsafeDev ✅ banner / prod+flag ✅ — the fourth row is a bonus guard-rail). Full suite 12 tests: 11 pass, 1 env-gated skip (whole-context smoke needs the real env — that gap is EB-35's scope). Note: the validator lives in the config-class constructor rather than a separate `ApplicationRunner`/`EnvironmentPostProcessor` (earlier + simpler; a Spring `@Configuration` class constructor fails during context refresh before any request is served). Merged to `main` commit `af50e58`; Jira EB-9 = Done.

<a id="p1-03"></a>
### P1-03 — JWT filter skips genuine public routes

**Priority:** P1 — stale/garbage `Authorization` headers currently 401 public endpoints (`JwtTokenValidatorFilter:54-57`) — public UX breaks on client bugs.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-16   **Domain:** authentication-security
**Components:** `JwtTokenValidatorFilter`, prod/dev chains

**Context:** The filter writes 401 before the authorization rules get a chance; public routes and authenticated routes currently share the filter's fate.
**Evidence:** ISSUES SEL-16; [`mds/authentication-security/security-configuration.md`](../../../mds/authentication-security/security-configuration.md).
**Expected outcome:** requests to explicit permitAll paths succeed regardless of `Authorization` header content; protected paths still enforce 401/403 as before.
**Approach:** introduce a public-path matcher (shared constant set mirroring the chain's permitAll list, defined in one place and referenced by both) and `shouldNotFilter` it; keep validator behavior on everything else.
**FE / API impact:** none.  **Decisions:** none.
**Depends on:** P1-02 (single-chain refactor reduces duplication).  **Blocks:** P2-03.
**Acceptance criteria:**
- [ ] IT: stale token + public endpoint → 200/intended; stale token + protected endpoint → 401.
**Docs:** security-configuration.md; ISSUE-MAPPING SEL-16.

<a id="p1-04"></a>
### P1-04 — Token from header only; credentials erased after auth

**Priority:** P1 — JWT duplicates in the response body (`AuthController:41`) and `eraseCredentials` comment removes a defense layer.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer + FE liaison sign-off
**Issues:** SEL-12   **Domain:** authentication-security
**Components:** `AuthController`, `ProviderManager` configs (both profiles)

**Context:** Returning the JWT in the body doubles exposure (logs, error trackers) and ties response caching to tokens.
**Evidence:** ISSUES SEL-12; [`mds/api/endpoints.md` §login](../../../mds/api/endpoints.md).
**Expected outcome:** `Authorization` header carries the token; body keeps `jwt` **only** behind a transitional property (`app.auth.return-token-in-body=true`) while the FE migrates; `eraseCredentialsAfterAuthentication` set true in both profiles.
**Approach:** set header on success handler; property-gated body echo; flip default to `false` at P7 release if FE confirms before then (tracked on task).
**FE / API impact:** contract-visible transitional — decision D-01 note; **coordination required.**
**Depends on:** —  **Blocks:** P7-06 checklist item (verify default flipped).
**Tests:** contract test: header present; body contains token only when property enabled; unit assert on ProviderManager config.
**Docs:** endpoints.md (login response), security-configuration.md; ISSUE-MAPPING SEL-12.

<a id="p1-05"></a>
### P1-05 — Error contract: catch-all→500, explicit 403, unified bodies

**Priority:** P1 — every failure today leaks either a raw internal message as 409 or is an undocumented 401-shape triplet.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead (contract) + Security Reviewer
**Issues:** SEL-14 (+SEL-03's unified-failure clause)   **Domain:** api / authentication-security
**Components:** `GlobalExceptionHandler`, `EduNestAuthenticationEntryPoint`, `EduNestAccessDeniedHandler` (new), `JwtTokenValidatorFilter` 401 writes

**Context:** The catch-all `Exception → 409 + e.getMessage()` handler turnsstack traces into API responses; WS-disabled HEAD `/error` leaves an effective 403 without body.
**Evidence:** ISSUES SEL-14; [`mds/api/error-responses.md`](../../../mds/api/error-responses.md) (full current-shape catalog).
**Expected outcome:** unexpected exceptions → 500 `{timestamp,status,error,message:"Internal server error",path}` (message sanitized, ref-id logged); explicit `AccessDeniedException` handler → 403 (same shape); all 401 writers emit the identical shape; validation errors keep field map (unchanged).
**Approach:** add `AccessDeniedException` handler; catch-all routes to 500 with generic body + MDC refId; align the three 401 writers to one shape constant; D-26 default (generic) adopted — FE sign-off recorded.
**FE / API impact:** contract-visible (409→500 change; body shape normalization) — transitional note per mapping relies on FE error rendering; coordination required.
**Depends on:** D-26 default confirmed (Phase 0 round or async).  **Blocks:** P2-06 contract tests; P7-02.
**Tests:** error-contract IT set: generic exception→500 shape; 403 shape; three 401 paths same bytes (modulo values).
**Docs:** `error-responses.md` rewrite; endpoints.md notes; ISSUE-MAPPING SEL-14.

> **P1-05 implemented (EB-12, 2026-10-29):** all five producers emit the one `ApiError` envelope (`{timestamp,status,error,errorCode,message,path,+refId on 500,+fields}`); `ApiErrorCode` catalog added. Catch-all leak FIXED: unhandled → 500 `ERR_INTERNAL_SYSTEM` with sanitized generic message + log-correlation refId (raw `e.getMessage()` never sent). Explicit `AccessDeniedException` advice handler → 403 `ERR_FORBIDDEN` (method-security denials no longer fall through as 409). Entry point/denied handler/JWT filter unified via `ServletErrorWriter` (three-401-dialect problem retired; `EduNest-denied-reason` header removed). Data-layer: MySQL index/column identifiers stripped → category text. `UserNotFound`/`UsernameNotFound` → fixed text (email echo retired); enumeration wording unification stays with EB-15/SEL-03. D-26: fields map SURVIVES nested inside the envelope for `ERR_VALIDATION`. WebSocket leak fixed: unexpected exceptions → generic text on `/queue/errors` (business `globalLogicEx` messages pass through). Probes E1–E6 (incl. no-leak assertions + three-producer shape pin) — 80/80 green on landing PR. FE ticket EF-2 created (unassigned — Shahd to pick when she reaches it).

<a id="p1-06"></a>
### P1-06 — OTP type isolation & verification scoping

**Priority:** P0 — codes are interchangeable across register / password-reset / account-delete flows (SEL-05 High).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-05   **Domain:** authentication-security
**Components:** `RegisterServiceImpl` (verify/send), `SettingsService`, admin account-delete flow, `OtpTokenRepository`

**Context:** `verifyUser:160-181` looks up any token for the email regardless of `otpType`; every flow purges all rows on resend; delete-account could be confirmed with a registration code. The fix is per-flow scoping, not a redesign.
**Evidence:** ISSUES SEL-05; [`mds/authentication-security/authentication-flow.md`](../../../mds/authentication-security/authentication-flow.md).
**Expected outcome:** each verification selects tokens of its own `otpType` only; each resender purges only its own type; a code minted for flow A cannot satisfy flow B.
**Approach:** add `otpType` to all token lookups/purges; verifyUser consumes `VERIFY`; restore/delete confirmations consume `VERIFY` (their own row); password reset handled by P1-07's RESET row; add repo query helpers.
**FE / API impact:** none.  **Decisions:** none.
**Depends on:** —  **Blocks:** P1-08 (verify-rate limiting uses same rows), P2-03.
**Tests:** IT cross-flow isolation: code from flow A rejected on B; resend leaves other flow's rows intact.
**Docs to update:** authentication-flow.md; ISSUE-MAPPING SEL-05.

<a id="p1-07"></a>
### P1-07 — Password reset: single-use, expiry-bound

**Priority:** P1 — `/reset-password` accepts any row (no type, no expiry, no consume) → reusable up to 1h.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-04   **Domain:** authentication-security
**Components:** `ForgetPasswordService`/reset endpoint service, `OtpTokenRepository`

**Evidence:** ISSUES SEL-04; authentication-flow.md (reset flow section).
**Expected outcome:** reset requires an unexpired row with `otpType=RESET` matching the email; the row is deleted on success (single-use).
**Approach:** select by (email, RESET, code, expiresAt>now); `deleteById` after successful password change; expired rows rejected with a dedicated message.
**FE / API impact:** error semantics only.  **Depends on:** P1-06 (type discipline).  **Blocks:** P2-03.
**Tests:** IT: expired → reject; reused → reject; fresh → success + row gone.
**Docs:** authentication-flow.md; ISSUE-MAPPING SEL-04.

**EB-14 implemented and merged (2026-10-29, PR #30, merge `52452a0`, Jira = Done):** OTP verification issues a one-time token; the final reset requires its versioned digest, unexpired `RESET` purpose and same-user lock, then consumes it atomically. The Team Lead approved the PR (including the five-minute `app.auth.reset-token-ttl` default) and merged to `main`. An injected token-deletion failure and rollback/credential reuse check were added before merge. Linux/JDK 21 CI on the final head `aeb834b`: **187/187**, zero failures/errors/skips ([run 40966140701](https://github.com/MooRaa86/EduNest/actions/runs/40966140701)). H2 metadata verified `otp_code` holds at least 67 characters — local evidence only. Pre-deployment follow-ups remain and are tracked on the Jira closure comment and in authentication-flow.md: frontend adoption of the `resetToken` contract, actual database column capacity and target-database concurrency verification, and old/new-instance rollout coordination. EB-15 retains throttling. No deployment is claimed.

<a id="p1-08"></a>
### P1-08 — Auth throttling + lockout + enumeration fix

**Priority:** P0 — unlimited `POST /login-api` and OTP endpoints make every credential compromise brute-forceable (SEL-03 Critical).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-03 (core half; P5-03 does platform-wide)   **Domain:** authentication-security
**Components:** new `AuthRateLimitFilter` (once-per-request, `ConcurrentHashMap` fixed-window + TTL), chain registration, `application*.properties` constants

**Context:** No rate limiting library exists in the pom (verified) — the dependency-free fixed-window filter keeps the P0 change auditable; constants externalized.
**Evidence:** ISSUES SEL-03; [`mds/authentication-security/security-risks.md`](../../../mds/authentication-security/security-risks.md).
**Expected outcome:** documented limits (e.g., login 5/5min per IP+account, OTP issue 3/10min, verify 5/15min, admin-register strict) return 429 with standardized body; login failure messages are enumeration-neutral (same body for unknown user / bad password).
**Approach:** filter with per-key counters, skip when disabled (`app.rate-limit.enabled`, off in tests via profile); register before auth filters; monospace keys `ip|route|account`; unify login failure bodies.
**FE / API impact:** new 429 response shape + login error unification (error docs updated).  **Decisions:** none (upgrade path = P5-03/bucket4j).
**Depends on:** P1-05 (shape unification) for body reuse.  **Blocks:** P5-03 (extension).
**Tests:** IT: threshold trips; window resets; enumeration probe shows identical bodies.
**Docs:** security-configuration.md + error-responses.md; ISSUE-MAPPING SEL-03.

> **P1-08 implemented (EB-15, 2026-10-30, merge `4529aeb`, PR #31, CI green, 86/86):** approved Omar spec — **8 requests/1 min** uniform on the six gated buckets (LOGIN `/login-api`, OTP_ISSUE, OTP_VERIFY, RESTORE_CONFIRM, REGISTER, ADMIN_REGISTER) instead of the draft differentiated table (per-bucket overrides remain property-tunable); **5 consecutive failed logins → 5-min account lock** (`LoginLockoutService`; provider records, resets on success); **10-min TTL** opportunistic sweep; **dynamic `Retry-After`** (window remainder ≤60 for `ERR_RATE_LIMITED`, lock remainder starting 300 for `ERR_ACCOUNT_LOCKED`). Enumeration: unknown-email/wrong-password/unverified/deleted all → one canonical `401 ERR_INVALID_CREDENTIALS` "Invalid email or password." (byte-identical, probe-pinned R4); no-role misconfig reclassified `IllegalStateException` → sanitized generic 500. Mechanic notes: body replay wrapper (`CachedBodyHttpRequestWrapper`) so the filter's email read never empties the controller's stream; filter registered in the prod chain via `addFilterAfter(HeaderWriterFilter)` (custom classes can't anchor addFilterBefore/After) + disabled `FilterRegistrationBean` + `COUNTED_ATTR` guard = no double counting; `app.rate-limit.enabled=false` in the test profile, `RateLimitProbesTest` R1–R6 opt back in at tight limits (3/2s, lockout 3→3s). FE ticket EF-3 (unassigned, High) carries countdown/Retry-After rules.

<a id="p1-09"></a>
### P1-09 — Admin credential exposure + guarded bootstrap

**Priority:** P0 — `GET /api/v1/admin` returns password hash & the register echo returns the plaintext password (SEL-08 High, API-04).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-08 (+API-04)   **Domain:** users-profiles / authentication-security
**Components:** `AdminService` (register `:49-55`, getAcc `:57-66`), `AdminController:33` (`@PreAuthorize` restore), admin authz URL rule

**Evidence:** ISSUES SEL-08/API-04; `mds/`sheets in [`users-profiles/`](../../../mds/users-profiles/MDS.md).
**Expected outcome:** admin responses contain neither hash nor plaintext password; the register endpoint stays public *only* while no admin exists (bootstrap guard) and is rate-limited (P1-08 covers); `@PreAuthorize("hasRole('ADMIN')")` restored on the controller.
**Approach:** response DTOs trimmed in service (never expose `getPasswordHash()`); guard on register: if any admin exists → 403; keep the bootstrap window documented in deployment docs (first-run procedure).
**FE / API impact:** admin account setup page (if any) must handle 403-after-bootstrap; coordination note.
**Depends on:** P1-08 (throttle on admin/register).  **Blocks:** —
**Tests:** contract: response field list assert; IT: registered admin → second register 403.
**Docs:** users-profiles/admin-accounts.md (or MDS.md), endpoints.md; ISSUE-MAPPING SEL-08/API-04.

<a id="p1-10"></a>
### P1-10 — Method-security & ownership sweep (admin/dashboard/room-create)

**Priority:** P1 — a set of controllers/services rely solely on URL rules or nothing at all: admin badge CRUD, mentor dashboard money/roster reads, dashboard stats, **room creation ownership (TD-07/G12)**.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-09, TD-07 (=G12)   **Domain:** authentication-security / gamification / ws
**Components:** `AdminBadgeController`, `MentorDashboardController` (getSalesChartData/getStudents), `MentorshipDashboardService#getStats`, `ChatRoomServiceImp#createRoom`, `AuthorizationRepository`

**Context:** Missing `@PreAuthorize` where an annotation would make intent independent of URL config drift; createRoom accepts any mentorship id without ownership validation.
**Evidence:** ISSUES SEL-09/TD-07; [`mds/authentication-security/authorization-rules.md` (G12 row)](../../../mds/authentication-security/authorization-rules.md).
**Expected outcome:** annotations consistent with URL rules everywhere above; createRoom rejects unowned mentorship ids (403) and logs a probe.
**Approach:** annotation pass (class-level where uniform); add `existsByMentorIdAndMentorshipId`-style ownership check in createRoom; keep URL rules unchanged (path normalization deliberately **not** done — breaking-change risk; see D-20 pattern).
**FE / API impact:** none (docs note renames rejected deliberately).  **Depends on:** —  **Blocks:** P2-03 role matrix.
**Tests:** role-matrix IT: outsider/mentor-B on mentor-A's mentorship → 403 for room create + dashboard reads.
**Docs:** authorization-rules.md; ISSUE-MAPPING SEL-09/TD-07.

<a id="p1-11"></a>
### P1-11 — Gate open push/mail endpoints

**Priority:** P0 — any authenticated user can push a notification to any user (`/api/v1/notifications/send`) and anyone can send platform mail (`/send`) — phishing & spam vectors.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-10 (=NOTIF-01), MAIL-01   **Domain:** notifications / integrations
**Components:** `NotificationController#sendNotification`, `PlatformMailController` (`@RestController("/mail")` bean-name quirk), prod config

**Context:** The mail controller's bean-name quirk makes it a *bare* `/send` route — obscure but real (dispatcher fact in [`mds/api/MDS.md`](../../../mds/api/MDS.md)). Both endpoints default to **admin-gated** per D-05 (delete if PO prefers).
**Evidence:** ISSUES SEL-10/MAIL-01; endpoints.md §1/§11.
**Expected outcome:** both endpoints return 403 for non-admin (or are removed); docs updated; G10 closed.
**Approach:** `@PreAuthorize("hasRole('ADMIN')")` + explicit security-chain path entry (defense in depth); service-level guard for wrapper reuse; if decision = delete: remove controllers and prune unused services (see NOTIF-05, INT-03 in P4-01/P1-26).
**FE / API impact:** any UI using these loses access (none expected; if FE uses them, it's admin UI — decision recorded).
**Depends on:** D-05 (default adopted).  **Blocks:** —
**Tests:** IT: student → 403; admin → 200.  **Docs:** endpoints.md; ISSUE-MAPPING SEL-10/MAIL-01/G10.

<a id="p1-12"></a>
### P1-12 — WebSocket room authorization + dead-code removal

**Priority:** P0 — any authenticated user can subscribe to `/topic/room/{id}` and post to any room via `/app/chat/...`: full chat impersonation/cross-read (SEL-primary WS-01).  **Size:** L
**Owner role:** Backend Developer (WS)   **Reviewers:** Security Reviewer + Eng Lead
**Issues:** WS-01 (+WS-02, TD-08=G13/D-14)   **Domain:** websocket
**Components:** `JwtChannelInterceptor` (destination rules on SUBSCRIBE/SEND), `ChatMessageService`, new membership query (`AuthorizationRepository` or chat repo), removal targets (`ChatRoomMemberRepository` dead injections per ISSUES TD-03)

**Context:** Membership semantics per **D-14 default**: write = explicit member or mentor-owner; read = additionally enrollment-proxy while `chat_room_members` is membership-lite. `isUserInRoom` exists only as a stub in the interceptor — this task makes it real. Frontend impact is *behavioral* only for violators (they get explicit errors instead of silent room access).
**Evidence:** ISSUES WS-01/WS-02/TD-08; [`mds/websocket/MDS.md`](../../../mds/websocket/MDS.md), [`messaging-flow.md`](../../../mds/websocket/messaging-flow.md).
**Expected outcome:** CONNECT (existing), SUBSCRIBE, SEND all validate membership; violations → message dropped + explicit ERROR frame (documented contract); dead commented interceptor code and unused injections removed; private `/app/chat.private` validates sender↔recipient pair.
**Approach:** parse destination per frame type in interceptor; centralize rule in a `ChatAuthorization` component; re-check in `ChatMessageService` (defense in depth); delete dead blocks; saveMessage only persists authorized sender.
**FE / API impact:** error-frame contract addition (documented in messaging-flow.md); no change for compliant clients.
**Depends on:** D-14 default (Phase 0).  **Blocks:** P2-05 WS suite; P7-02.
**Tests:** WS IT (P2-05): foreign subscribe/send → ERROR frame + no persistence; member flows unchanged.
**Docs:** websocket/*; ISSUE-MAPPING WS-01/02, TD-08, G13.

<a id="p1-13"></a>
### P1-13 — Conversation IDOR trio + server-derived event routing

**Priority:** P0 — read any conversation (`CHAT-02`), edit anyone's message (`CHAT-03`), spoof the recipient of message-saved events (`CHAT-04`) — all live unterminated.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** CHAT-02, CHAT-03, CHAT-04   **Domain:** websocket / users (direct messages)
**Components:** `ConversationService` (history `getConversationMessages`, `editMessage`), `ConversationEventListener`/queue push (recipient derivation), conversation REST controllers

**Context:** [`mds/users-profiles/profiles.md`](../../../mds/users-profiles/profiles.md) documents conversation restore-points: ownership check commented out in history; event recipient taken from client DTO.
**Evidence:** ISSUES CHAT-02/03/04.
**Expected outcome:** (a) history only for conversation participants (student or mentor of the pair, ADMIN full); (b) `editMessage` validates sender==requester (restore-kept check); (c) saved-message events derive recipient email from the persisted conversation record, never the client payload; emergency-alert reception unaffected.
**Approach:** ownership predicate helper; remove client `recipientEmail` from DTO reliance (DTO field *kept* but ignored — contract-preserving); server asserts pair before event send.
**FE / API impact:** none for compliant clients (spoofed events stop; honest behavior unchanged).
**Depends on:** —  **Blocks:** P2-03 IDOR suite rowset.
**Tests:** three IT denial cases + honest-client happy path.
**Docs:** users-profiles/profiles.md (conversation section); ISSUE-MAPPING CHAT-02/03/04.

<a id="p1-14"></a>
### P1-14 — Content-read guards (lectures / tasks / get-tasks / detail)

**Priority:** P1 — unowned/unenrolled users read lectures, task details, `.get-tasks` and can filter by mentorship id (MSH-02, LEARN-02; G4/G5/G6).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer + Eng Lead
**Issues:** MSH-02, LEARN-02 (D-03 confirm)   **Domain:** learning-content / mentorship-flow
**Components:** `LectureService` GETs ×3, `TaskController` detail/filter/`{msid}/{taskId}/student`, `getMentorShipTasks`, shared `ContentAccessGuard` helper (new)

**Context:** One shared guard (enrolled-student OR owning-mentor OR ADMIN) applied consistently; filter endpoints additionally check the mentorship id belongs to the requester's scope.
**Evidence:** ISSUES MSH-02/LEARN-02; [`mds/learning-content/...`](../../../mds/learning-content/MDS.md), [`authorization-rules.md`](../../../mds/authentication-security/authorization-rules.md) G4–G6.
**Expected outcome:** every listed read returns 403 (404 where detail semantics prefer) for outsiders; identical content for authorized roles as today.
**Approach:** guard helper using enrollment+ownership repositories; wire into the 8 endpoints; document matrix in authorization-rules.md.
**FE / API impact:** none for legit flows (dashboards already limit to own mentorships).
**Depends on:** D-03 default confirmed.  **Blocks:** P2-03.
**Tests:** matrix IT: enrolled ✅, owning mentor ✅, other student ❌, anonymous ❌.
**Docs:** learning-content MDS + authorization-rules; ISSUE-MAPPING MSH-02/LEARN-02/G4-G6.

<a id="p1-15"></a>
### P1-15 — DRAFT mentorship confinement

**Priority:** P1 — drafts are publicly readable (detail + overview) pre-publish (MSH-01, G8).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** MSH-01 (D-02 default (a))   **Domain:** mentorship-flow
**Components:** `MentorShipServiceImp#getMentorShipById`, overview/public overview queries, explore already ACTIVE-only (verify)

**Evidence:** ISSUES MSH-01; [`mds/mentorship-flow/MDS.md`](../../../mds/mentorship-flow/MDS.md).
**Expected outcome:** DRAFT detail returns 404 for any caller except owner-mentor/ADMIN; public overview excludes DRAFT; explore unchanged (ACTIVE-only).
**Approach:** status guard in detail; `status <> DRAFT` predicate on overview query; tests.
**FE / API impact:** FE "preview draft" only for owners (expected; note).
**Depends on:** D-02 default.  **Blocks:** P2-03.
**Tests:** probes: anonymous + enrolled-student + other-mentor → 404; owner → 200.
**Docs:** mentorship-flow MDS; ISSUE-MAPPING MSH-01/G8.

<a id="p1-16"></a>
### P1-16 — Leaderboard guard + reviews/emails privacy

**Priority:** P1 — leaderboards open to anyone (MSH-03/G7) and review/public/project surfaces expose student emails (MSH-09, USR-02).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer + PO (privacy sign-off)
**Issues:** MSH-03 (=GAME-01+G11 partial), MSH-09, USR-02 (D-04 default (a))   **Domain:** mentorship-flow / users-profiles
**Components:** `MentorshipLeaderboardService` guards (re-enable commented pass), review projections (student name, not email), public project DTOs

**Evidence:** ISSUES MSH-03/MSH-09/USR-02; [`mds/mentorship-flow/MDS.md`](../../../mds/mentorship-flow/MDS.md).
**Expected outcome:** leaderboard reads gate on enrollment/ownership/admin; review surfaces expose display names only; no emails in any public payload (grep-verified).
**Approach:** re-enable membership check with message; project `student.SimplfiedUserInfo` style DTO (name+avatar id); endpoints.md notes.
**FE / API impact:** review UI must render `name` field instead of `email` — **coordination required** (D-04).
**Depends on:** D-04 default.  **Blocks:** P2-03.
**Tests:** outsider → 403 leaderboard; anonymous list reviews → no email fields (payload asserts).
**Docs:** mentorship-flow/MDS.md (leaderboard + review sections); ISSUE-MAPPING MSH-03/MSH-09/USR-02/G7/GAME-01.

<a id="p1-17"></a>
### P1-17 — Homepage progress endpoint enrollment check

**Priority:** P1 — progress percent leaks summary numbers for any mentorship id (MSH-11).  **Size:** XS
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** MSH-11   **Domain:** mentorship-flow
**Components:** `HomePageService#getStudentMentorshipProgress`

**Evidence:** ISSUES MSH-11; mentorship-flow docs.
**Expected outcome:** 403 without enrollment; unchanged for enrolled students.
**Depends on:** —  **Blocks:** —
**Tests:** probe pairs.  **Docs:** ISSUE-MAPPING row + mentorship-flow note.

<a id="p1-18"></a>
### P1-18 — Quiz answer-key scoping

**Priority:** P0 — `/api/v1/my-answers/{quizId}` returns `correctAnswer` for every question before submission: cheating everything points-based (LEARN-01).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer + Eng Lead
**Issues:** LEARN-01   **Domain:** learning-content
**Components:** `QuizSubmissionServiceImpl#getStudentQuizReview` (`:196-247`), answer DTO mapping, mentor answer-view `GET /api/v1/answer/{studentId}/{quizId}` (ownership assert)

**Context:** The clean rule: `correctAnswer` is present only once a **submitted** attempt exists for that (student, quiz). Mentor views keep full keys but must assert quiz ownership (currently implicit via controller class annotation — add explicit test).
**Evidence:** ISSUES LEARN-01; [`mds/learning-content/quizzes-workflow.md`](../../../mds/learning-content/quizzes-workflow.md).
**Expected outcome:** pre-submission review payload contains no correct-answer fields (field contract: present-with-value vs. absent — verify with FE which shape they want; default = field omitted); post-submission behavior unchanged; mentor view ownership-asserted.
**Approach:** conditional projection: build per-question DTO without `correctAnswer` when no submitted `QuizSubmission` exists; single query check cached per request; endpoint docs updated.
**FE / API impact:** my-answers panel display pre-submit — verify FE renders gracefully (they asked and got zeros historically; note recorded).
**Depends on:** FE confirm (non-blocking: omission is strictly less data).  **Blocks:** P2-04 quiz flow tests.
**Tests:** IT: pre-submit payload has no correctAnswer; post-submit has; mentor foreign quiz → 403.
**Docs:** quizzes-workflow.md + endpoints.md; ISSUE-MAPPING LEARN-01.

<a id="p1-19"></a>
### P1-19 — Question re-parent guard + badge/award read scoping

**Priority:** P1 — question PATCH can move content into another mentor's quiz (QZ-06/G15, ⚠️ V-06 first) and badge/award listings are unscoped (GAME-02/G11).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** QZ-06, GAME-02   **Domain:** learning-content / gamification
**Components:** `QuestionServiceImp#updateQuestion`, `BadgeAwardController` GETs, `BadgeService`

**Evidence:** ISSUES QZ-06/GAME-02; quizzes-workflow.md §1; [`mds/gamification/MDS.md`](../../../mds/gamification/MDS.md).
**Expected outcome:** updateQuestion validates target quiz ownership (reject cross-mentor); badge/award reads scoped (mentor owns badge OR enrolled student OR admin); V-06 verdict governs exact predicate.
**Approach:** after V-06: load target quiz, compare owner mentor to requester; badge award queries gain scoped predicates; badge *catalog* GETs stay public (PR-safe: no personal data).
**Depends on:** P0-08 (verdict).  **Blocks:** —
**Tests:** cross-mentor PATCH → 403; award list outsider → 403.
**Docs:** quizzes-workflow.md, gamification MDS; ISSUE-MAPPING QZ-06/GAME-02/G11/G15.

<a id="p1-20"></a>
### P1-20 — Generic file endpoint guard

**Priority:** P1 — `GET /files?filePath=` is an unauthenticated path-traversal-style read gate (LEARN-03/G9 part of FILE-01 complexity).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** LEARN-03 (D-06 default: guard now, retire later)   **Domain:** file-storage
**Components:** `FileController`, `FileAccessService` allowlist map

**Evidence:** ISSUES LEARN-03/FILE-01; [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md).
**Expected outcome:** endpoint resolves path via the allowlist first; ownership checks where the category demands (submissions); non-allowlisted keys → 404; traversal suites (existing) still pass.
**Approach:** route through `FileAccessService` resolution (same service used by typed endpoints); add ownership branch for submission/assignment categories; log attempts with foreign keys.
**Depends on:** D-06 default.  **Blocks:** P7-02.
**Tests:** probes: public-category stays; submission category w/o ownership → 404; traversal → 404.
**Docs:** file-storage MDS + endpoints.md; ISSUE-MAPPING LEARN-03/G9 (FILE-01 remainder → P1-21).

**Reconciliation (2026-10-29):** Jira EB-27 remains **To Do**. [PR #23](https://github.com/MooRaa86/EduNest/pull/23) merged the temporary generic-route denial as `171c6a1`; this safeguard does not implement the D-06 guard-first contract. Frontend migration and eventual retirement remain unverified.

<a id="p1-21"></a>
### P1-21 — Upload exposure policy (public/private split)

**Priority:** P0 — every path under `uploads/**` — including **task submissions** — is anonymously fetchable via both the security chain and the `WebConfig` resource handler (FILE-01 Critical).  **Size:** M
**Owner role:** Backend Developer (steward)   **Reviewers:** Security Reviewer + Eng Lead + FE liaison note
**Issues:** FILE-01 (+TD-09=G14 folder-name mismatch)   **Domain:** file-storage
**Components:** `ProjectSecurityProdconfig` (URL rules), `WebConfig#addResourceHandlers`, folder-name constants (`ImageStorageService`), dev-chain parity

**Context:** Two independent mechanisms serve `/uploads/**` (chain `permitAll` + Spring MVC resource handler); both must be constrained identically or the fix is illusory. Final public set per D-07: `mentorship-cover-images`, `student`, `mentor`, `chatroom` (folder names as created; TD-09 mismatch resolved by *policy constants*, not folder **renames** — FE relies on URLs).
**Evidence:** ISSUES FILE-01/TD-09; [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md); P0-06 matrix (which folders contain what).
**Expected outcome:** anonymous fetch of `uploads/submissions/**`, `uploads/task-attachment/**`, `uploads/project-attachment/**` (and any future private class) denied at *both* layers; public categories unchanged to FE; submissions accessible only via ownership-checked endpoints (`/files/**` typed routes); folder-policy constants defined in one place.
**Approach:** replace broad `/uploads/**` rules with enumerated public pattern list in both chain and resource handler; add `/files/**` typed route usage docs; FE note (submission link change) recorded on task.
**FE / API impact:** **yes — submissions links must switch to `/files/task-submissions/...` (existing route) or authenticated fetch; coordination required (D-07).**
**Depends on:** D-07 default + P0-06 matrix.  **Blocks:** P7-02 staging file-access validation.
**Tests:** IT: anonymous submission fetch → 401/403 at both layers; anonymous cover fetch → 200; owned submission via typed route → 200.
**Docs:** file-storage/MDS.md + `authorization-rules.md`; ISSUE-MAPPING FILE-01/TD-09/G14.

**Reconciliation (2026-10-29):** Jira EB-28 remains **In Progress**. [PR #23](https://github.com/MooRaa86/EduNest/pull/23) merged to main as `171c6a1083488cf9a947ed5b21b3f2d79c56752c`, including draft-aware attachment checks. Final head `e716f9dfbe06c12f0907f9fa0954ab820dbb659b` passed [Linux/JDK 21 CI](https://github.com/MooRaa86/EduNest/actions/runs/40956840871): 177 tests, zero failures/errors/skips. One GitHub owner approval is recorded; a distinct Security Reviewer approval and frontend liaison agreement are not evidenced. Frontend migration, admin-image contract, and filesystem/staging assessment remain outstanding; Lead/Security must clarify the D-07 acceptance boundary with EB-83. [PR #24](https://github.com/MooRaa86/EduNest/pull/24) separately merged caller-scoped nullable task `submissionId` (`641bb6516a29a7bea93615d604752b1df5d9c51b`), with [CI green](https://github.com/MooRaa86/EduNest/actions/runs/40890628874). Supabase Storage is separate EB-83 work; no deployment is claimed.

<a id="p1-22"></a>
### P1-22 — Upload validation hardening (content-type by bytes)

**Priority:** P1 — image upload paths trust client `Content-Type`; cover extension from filename (`FILE-02` High, `FILE-03/04` + `MSH-06`).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** FILE-02, FILE-03, FILE-04, MSH-06 (V-04/V-05 evidence)   **Domain:** file-storage
**Components:** `ImageStorageService` (sniffing + per-surface mime sets), `FileStorageService` (zip octet-stream tighten), limit properties

**Evidence:** ISSUES FILE-02, FILE-03, FILE-04 (`**F-rows**` referenced from P1-22); [`mds/file-storage/MDS.md`](../../../mds/file-storage/MDS.md); P0-06 matrix results.
**Expected outcome:** stored bytes never diverge from declared type on image surfaces (Tika detect on first bytes like `FileStorageService`); extension derived from detected type; per-surface allowlists (jpg/png/webp covers+avatars; jpg/png chat images); zip accepted only with real zip magic; re-run of the P0-06 matrix shows hardened outcomes.
**Approach:** inject same detection util; reject mismatches with 400; wire size limits (reuse existing property patterns).
**FE / API impact:** none for valid files; mislabeled uploads now 400 (error docs updated).
**Depends on:** P0-06/P0-07.  **Blocks:** —
**Tests:** matrix re-run IT (mislabeled/zip/double-ext → rejected).
**Docs:** file-storage/MDS.md; ISSUE-MAPPING FILE-02/03/04, MSH-06.

**EB-29 reconciliation (2026-10-30, implementation complete on [PR #33](https://github.com/MooRaa86/EduNest/pull/33), merge + CI pending):** the shared `service/fileSotageService/UploadContentValidator` implements the expected outcome exactly as scoped — JDK-decoder-backed magic-byte detection for jpg/png (JPEG marker walk to SOS + full `ImageIO` decode; PNG signature + IHDR CRC32 + decode), structural webp identification (no JDK decoder — deliberate residue), ImageIO-decode-failure and size-limit rejection per EB-12 envelope (`globalLogicEx` → 400 `ERR_BUSINESS_RULE`); `ImageStorageService` derives the stored extension from **detected content** (photo.jpg.exe with real JPEG bytes normalizes to `{id}-EduNest-{uuid}.jpg` — FILE-02/03/MSH-06 spoofer loses filename control; URL contract unchanged for legitimate uploads); declared-MIME cross-check (canonical set per detected format; missing/`octet-stream` declarations ignored); `FileStorageService` zip allowlist tightened (`octet-stream` removed) with structural central-directory verification on the temp file before move. Mislabeled/BLOCKED-MIME paths on FileStorageService now answer 400 (was sanitized 500), per P0-06 convention. Provider-recommended extra mortgages (JPEG marker-walk header completeness, PNG IHDR CRC, WebP subtype containment, ZIP central-directory parse) are in. Additional in-scope hardening surfaced by probing: all nine replace flows (cover, student/mentor/admin avatars, chatroom, task/project attachments, task/project submissions) flipped to save-new-before-delete-old so a rejected upload can no longer destroy the previously stored file (P0-06 convention "upload normalization destroying replacement victims"); permanent-damage removal for the deep window remains P3-03. Regression: new `UploadValidationHardeningProbeTest` (28 probes — real fixture acceptance + byte-identical serve-back across surfaces, 8 malformed/truncated/tampered-CRC rejections with no file/`.tmp`/metadata remnants, 8 ZIP-destination integrity cases incl. genuine-zip-named-`.pdf`, local-header-only archive, and an end-to-end API-level task-submission spoofing rejection with `submissions` folder_journal-count assertion) + `UploadMatrixHarness`/`UploadExposurePolicyProbeTest` fixture adjustments to keep EB-28 pins intact; **full Maven suite 221 tests / 0 failures / 0 errors** on the PR branch (Windows run; CI verdict pending on PR #33). Error docs updated (`mds/api/error-responses.md` upload rows now 400 `ERR_BUSINESS_RULE`); ISSUE-MAPPING FILE-02/03/04 + MSH-06 rows flipped to Hardened. Jira EB-29 stays In Progress-to-merge (not Done) until PR merge + green CI.

<a id="p1-23"></a>
### P1-23 — CORS allowlist

**Priority:** P2 — prod currently trusts `*` with credentials (SEL-11 Medium).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-11 (D-23: values needed)   **Domain:** authentication-security / deployment
**Components:** prod config CORS bean, env `CORS_ALLOWED_ORIGINS`

**Evidence:** ISSUES SEL-11; security-configuration.md.
**Expected outcome:** credentialed preflight from unlisted origins → rejected; listed origins work as today; wildcard never combined with credentials mode.
**Approach:** read origins from property (comma list); default empty-fail in prod unless provided; document required value in deployment docs.
**Depends on:** D-23.  **Blocks:** P7-05 config gate.
**Tests:** CORS IT (config-driven).
**Docs:** security-configuration.md + environment-configuration.md; ISSUE-MAPPING SEL-11.

<a id="p1-24"></a>
### P1-24 — Contact-reply escaping

**Priority:** P2 — suspected HTML injection in admin contact replies (SEL-15; P0-04 verdict governs; fix regardless — escaping is cheap).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer
**Issues:** SEL-15 (V-02 evidence)   **Domain:** notifications / users (contact)
**Components:** `ContactMessageService` reply template merge (contrast: badge flow's `HtmlUtils` escape)

**Evidence:** ISSUES SEL-15; [`mds/notifications/MDS.md`](../../../mds/notifications/MDS.md).
**Expected outcome:** merged user fields render inert in email HTML clients; test payload appears escaped.
**Approach:** escape at merge time (same pattern as badge notification flow); add unit test with hostile payload; if P0-04 refutes, still merge escaping (defense) and record refutation.
**Depends on:** P0-04 (parallelizable; fix unconditional).  **Blocks:** —
**Tests:** payload→escaped assertion.  **Docs:** notifications/MDS.md; ISSUE-MAPPING SEL-15.

<a id="p1-25"></a>
### P1-25 — Bean validation coverage (login / verify-user / resend)

**Priority:** P2 — INV-02-documented gaps: missing `@Valid`, dead DTO annotations (SEL-07 Medium).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Backend Lead
**Issues:** SEL-07   **Domain:** api / authentication-security
**Components:** `AuthController` (login/verify-user/resend), DTOs (`LoginRequestDto` legacy/`PasswordMatches` note)

**Evidence:** ISSUES SEL-07; [`mds/api/error-responses.md` §validation](../../../mds/api/error-responses.md).
**Expected outcome:** malformed login/verify/resend payloads → 400 field map; `PasswordMatches` legacy validator replaced by modern pattern (delete dead class or rebind — keep one).
**Approach:** `@Valid` + modern constraint annotations on current DTOs; swap compare to modern DTO for login (keep field names FE-stable).
**FE / API impact:** 400 bodies appear where silent `try` branches persisted; error docs updated.
**Depends on:** —  **Blocks:** —
**Tests:** contract tests for each malformed shape.
**Docs:** error-responses.md; ISSUE-MAPPING SEL-07.

<a id="p1-26"></a>
### P1-26 — Dead-dependency & dead-code hygiene

**Priority:** P2 — removes JavaFX (`INT-01`, `TD-04`) + unused `io.agora:authentication:2.1.3` (planning-discovery, R11/A-8), dead template `INT-03`, dead DTO/projections/util dupes (`TD-02/03/05`, `NOTIF-05` partial).  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** INT-01, INT-03, TD-02, TD-03, TD-05 (+TD-04)   **Domain:** integrations / tech-debt
**Components:** `pom.xml`, `templates/certificate-template.html` (INT-03 — **keep file**, mark D-11 dependency: PDF decision may want it; instead add doc note), unused util consolidation, `System.out` prints → logger placeholders

**Context:** Deletion ordering matters: run *after* P1-12/13 (dead WS lines referenced by those fixes). The Agora lib: zero Java references (verified); removal gated only on P0-01's A-8 intent note; if intent unknown → keep pom line but note in docs, revisit D-16.
**Evidence:** ISSUES INT/TD rows; [`mds/integrations/external-services.md`](../../../mds/integrations/external-services.md); `pom.xml` (JavaFX + `io.agora:authentication:2.1.3`, zero Java refs — verified); `.gitignore` already ignores `uploads/` (no action).
**Expected outcome:** `mvn dependency:analyze` shows no unused declared deps (JavaFX/Agora removed if cleared); compile green; no `System.out` in touched files; `utils/util` single package (merge modest classes only).
**Approach:** extraction over deletion for guarded classes (only delete where audit marked safe); logging placeholders via slf4j `LoggerFactory` (P5-02 formalizes).
**Depends on:** P1-12, P1-13 landed.  **Blocks:** —
**Tests:** context boot + smoke compile; `dependency:analyze` run attached.
**Docs:** integrations/external-services.md; ISSUE-MAPPING rows.

<a id="p1-27"></a>
### P1-27 — Session invalidation & refresh (conditional)

**Priority:** P1 — 7-day non-revocable tokens (SEL-06 High) → decision D-01 shapes the answer; **this task proceeds on the default (option (a): short access + refresh, with (b) compiler-simple fallback).**  **Size:** L
**Owner role:** Backend Developer   **Reviewers:** Security Reviewer + Eng Lead
**Issues:** SEL-06 (D-01)   **Domain:** authentication-security
**Components:** `JwtService`, new `/login-api/refresh` endpoint, `AuthProvider`/validator (join claims), optional `lastInvalidatedAt` fallback column

**Context:** Default path adds a refresh endpoint + 30-min access tokens; transitional: legacy 7-day tokens remain valid *until* each user's next refresh (no forced global invalidation — no data migration risk).
**Evidence:** ISSUES SEL-06; security-risks.md.
**Expected outcome:** refresh issues short tokens; user logout (delete-account/restore, disable, admin action) invalidates future access (validator consults `lastInvalidatedAt` — or D-01(b) pattern if chosen); 401 + redirect-to-login contract documented.
**FE / API impact:** **contract-visible** — refresh flow requires FE change; transitional default keeps current tokens valid to avoid a big-bang (documented window until P7 release).
**Depends on:** D-01 (Phase 0 target).  **Blocks:** P6-03 secret rotation notes (same re-auth window).
**Tests:** IT: refresh lifecycle; invalidate-on-disable; legacy token acceptance window.
**Docs:** security-configuration.md + endpoints.md; ISSUE-MAPPING SEL-06/D-01 row.

---

**Task-ID index:** P1-01 … P1-27 (27 tasks; 10×P0, 13×P1, 4×P2). Resources: [`../phase-02-testing-and-ci/tasks.md`](../phase-02-testing-and-ci/tasks.md) defines the suites that lock these in.
