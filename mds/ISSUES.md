# ISSUES.md — Consolidated Issue Inventory

**Status:** Oct 2026 (documentation phase). Every entry was verified against the current source tree; severities reflect *production* impact priority. **No fixes were made during this phase** — this inventory is the baseline for the stabilization phase.

## How to read

- **Status:** ✅ Confirmed (reproduced by reading code, with evidence) · ⚠️ Suspected (needs runtime/FE confirmation).
- **Severity:** Critical → immediate exploit/data risk; High → production-blocking; Medium → hardening; Low → debt.
- **Categories (canonical):** SECURITY · BUG · DATA INTEGRITY · RELIABILITY · PERFORMANCE · TECHNICAL DEBT · TEST GAP · DEPLOYMENT · DOCUMENTATION.
- **ID families:** `SEL` auth/security · `WS` websocket infra · `CHAT` chat/conversations · `MSH` mentorship · `LEARN` tasks/projects/quizzes · `QZ` quiz deep-cuts · `USR` users/profiles · `GAME` gamification · `LS` live sessions · `CERT` certificates · `NOTIF` notifications · `MAIL` platform mail · `FILE` file storage · `DB` persistence · `API` API layer · `INT` integrations · `DEP` deployment · `TST` testing · `DOC` documentation · `TD` naming/dead-code debt · `ARCH` cross-cutting rollups (reference rows) · `G#` authorization-gap matrix rows.
- **Cross-references:** rows marked *(xref)* duplicate a canonical ID to satisfy references made in a domain doc; they are excluded from tallies.

All paths relative to repo root. Domain details: each domain's MDS under its directory (`mds/<domain>/MDS.md`).

---

## 1. Security & Authentication (SEL)

| ID | Cat | Sev | Status | Issue | Evidence | Recommended resolution |
|---|---|---|---|---|---|---|
| SEL-01 | SECURITY | Critical | ✅ | Hardcoded JWT fallback secret committed; `LoginServiceI#getJwtRole` hardcodes the *default* secret even when `JWT_SECRET` env differs → login role parsing fails with any non-default secret. Also: no startup validation for short secrets | `src/main/java/com/example/gradproj/EduNest/utils/Constants.java:5`; `service/auth/LoginServiceI.java:42-47`; `service/jwt/JwtService.java:165-167` | Remove default; read env-only + fail-fast validation; align role parse with `JwtService` |
| SEL-02 | SECURITY | Critical | ✅ | `!prod` security profile grants `anyRequest().permitAll()` (+httpBasic, formLogin, csrf disabled, sessionful) — every endpoint incl. admin/lectures/files is open whenever `prod` isn't active | `config/security/ProjectSecurityConfig.java:53` | Collapse to one hardened chain; fail-fast when running without `prod`; retire dev chain or gate it behind an explicit `dev-unsecure` profile |
| SEL-03 | SECURITY | Critical | ✅ | No rate limiting/lockout on login, OTP issue/verify/resend; distinct error messages enumerate accounts (404 vs 401 vs 400) | `config/auth/EduNestUserDetailsService.java:59`; `RegisterServiceImpl` OTP paths; `GlobalExceptionHandler:192-199` | Throttle per IP+account; time-lockouts on OTP attempts; unify auth-failure responses |
| SEL-04 | SECURITY | High | ✅ | Password-reset final step requires no secret: presence of a `RESET` OTP row + 1-hour `createdAt` window; `expiresAt` is not re-checked at reset | `RegisterServiceImpl.resetPassword:230-245` | Bind reset to a one-time signed token consumed at reset; enforce expiry |
| SEL-05 | SECURITY | High | ✅ | OTP defects: `verifyUser` doesn't filter `otpType` (any pending OTP verifies an account); codes stored plaintext (6-digit, 2-min); single row per user → any new OTP deletes pending RESET/DELETE rows mid-flow | `RegisterServiceImpl.verifyUser:160-181` (lookup without type), `processAndSendOtp:51`; `entity/register/OTP.java` | Filter by type (+purpose-scoped rows or hash codes); rate-limit verify; keep flows isolated |
| SEL-06 | SECURITY | High | ✅ | No logout / refresh tokens / server-side invalidation; 7-day HS256 tokens; authorities claimed in token stay authoritative until re-login; WS CONNECT doesn't re-check enabled/deleted | `JwtService.java:33,84-108`; `ProjectSecurityProdconfig` ; repo-wide grep (no refresh) | Introduce short-lived access + refresh, or last-issued-at invalidation column checked in validator/CONNECT |
| SEL-07 | TECHNICAL DEBT | Medium | ✅ | Missing `@Valid` on `/login-api` (`AuthController:34`) and `/register/verify-user` (`UserManagementController:53`); no `@Validated` anywhere → resend-OTP `@Email` constraint unenforced; `PasswordMatches` validator bound only to a legacy unused DTO | cited lines; grep `@Validated` = 0 | Annotate + adopt `@Validated` on method-param validation; wire modern DTOs |
| SEL-08 | SECURITY | High | ✅ | **FIXED (EB-16):** admin exposure resolved — credentials dropped from DTOs (register response no longer echoes plaintext, admin list no longer exports hashes), one-shot synchronized bootstrap with audited text, last-admin deletion guard closes the bootstrap re-open hole, `deleteAdmin` made transactional (was a bare 500). Route-level `hasRole("ADMIN")` covers the list endpoint; the DTO removal makes the commented-out annotation moot. | branch `feat/p1-09-admin-bootstrap-protection`; probes `AdminBootstrapProbesTest` (7); see ISSUE-MAPPING SEL-08 | (resolved in EB-16) |
| SEL-09 | SECURITY | Medium | ✅ | AdminBadge endpoints rely on prod URL rules only (no method security; `/api/admin/**` patterns inconsistent); admin-badge GETs readable by any authenticated user | `controller/admin/AdminBadgeController`, `ProjectSecurityProdconfig:99-107` | Add `@PreAuthorize` + normalize paths |
| SEL-10 | SECURITY | High | ✅ | **DELETED (EB-18 / P1-11):** `POST /api/v1/notifications/send` deleted per Decision D-05 (now 404 Not Found); closes user-to-user arbitrary notification spam/phishing vector. | `FileNotifyBadgeProbesTest.g10_userPushesNotificationToAnyone` | (resolved in EB-18) |
| SEL-11 | SECURITY | Medium | ✅ | Prod CORS reflects any origin + `allowCredentials(true)`; dev CORS `*` (no creds). Benign while header-only auth; footgun once cookies exist | `ProjectSecurityProdconfig:50-63`; `ProjectSecurityConfig:70-75` | Whitelist origins; forbid credentialed wildcard |
| SEL-12 | SECURITY | Medium | ✅ | JWT also returned in login response **body**; `ProviderManager` keeps credentials (`eraseCredentialsAfterAuthentication=false`) in both profiles | `AuthController:41`; both configs | Return header only; erase credentials |
| SEL-13 | SECURITY | Medium | ✅ (latent) | WS handshake design reads JWT from URL query param — tokens in access logs/Referer if the dead interceptor is ever registered (SockJS fallback motivation is real) | `JwtHandshakeInterceptor.java:36-39`; registration absent (`WebSocketConfig:34-38`) | Replace with short-lived single-use ticket flow if needed |
| SEL-14 | SECURITY | High | ✅ | Catch-all `@ExceptionHandler` maps **every** unhandled error to **409 Conflict** with raw `getMessage()` (info disclosure + wrong status); `AccessDeniedException` from method security also lands here (409 instead of 403) | `GlobalExceptionHandler:223-226` | Map to 500 + generic body; add explicit 403 handler |
| SEL-15 | SECURITY | Medium | ⚠️ | Admin reply/moderation HTML/email templates merge unescaped values (HTML injection into emails; contact fields revert without escaping) | `ContactMessageService:111` (merge only); contrast `AdminBadgeService` using `HtmlUtils` | Escape all template inputs |
| SEL-16 | BUG | Medium | ✅ | **FIXED (EB-10 / P1-03):** Unified `PublicRoutesPolicy` single source of truth for public routes (APIs + public uploads). `JwtTokenValidatorFilter` catches invalid/stale/garbage JWTs and gracefully proceeds anonymously for public routes instead of returning 401; protected routes continue to reject with 401 `ERR_INVALID_TOKEN`. Dual-mode routes preserve authentication context when a valid token is present. Probed by 35 tests in `PublicRouteMatrixProbeTest`. | `PublicRoutesPolicy.java`; `ProjectSecurityConfig.java`; `JwtTokenValidatorFilter.java`; `PublicRouteMatrixProbeTest` | (resolved in EB-10) |

**Authorization-gap matrix (G#)** — cross-verified object-level gaps (detailed in [authentication-security/authorization-rules.md §3](authentication-security/authorization-rules.md#3-verified-object-level-gaps-idor-style)); canonical IDs shown:

| G# | Canonical | Subject (one-line) |
|---|---|---|
| G1 | WS-01 | STOMP room subscribe/send unguarded |
| G2 | CHAT-02 | Conversation history IDOR |
| G3 | CHAT-03 | Conversation edit IDOR (check commented) |
| G4 | MSH-02 | Lecture reads unguarded |
| G5 | MSH-02b* | Mentorship task list unguarded (folded into MSH-02) |
| G6 | LEARN-02 | Task/project detail + `/filter/{msid}` unguarded |
| G7 | MSH-03 | Mentorship leaderboard guard disabled |
| G8 | MSH-01 | DRAFT mentorship exposure (`getMentorShip`, public overview) (CLOSED — EB-22 / P1-15, D-02 (a) confinement) |
| G9 | FILE-01/LEARN-03 | `/files?filePath=` ownership bypass |
| G10 | SEL-10 | Notifications send: any authenticated user → any email's queue (CLOSED — EB-18 / P1-11 deleted per D-05) |
| G11 | GAME-02 | Badge/award reads unguarded (CLOSED — EB-26 / P1-19: `/badge-awards/student/{id}` confined to self/ADMIN/mentor-of-enrollment; mentorship+badge award reads were already owner-only; badge catalog stays open per plan — no personal data) |
| G12 | TD-07 | Room creation ownership gap (CLOSED — EB-17 / P1-10: `createRoom` enforces `SecurityService#isMentorOwnMentorship` before creation — foreign/non-existent id → 403, owner → 200; probes `MethodSecuritySweepProbesTest`) |
| G13 | TD-08 | Room-membership = 3 inconsistent semantics |
| G14 | TD-09 | Profile-image folder name mismatch (relying on catch-all public route) |
| G15 | QZ-06 | Question re-parent target-quiz ownership gap (CLOSED — EB-26 / P1-19: `updateQuestion` enforces `isMentorOwnQuiz` on the TARGET quiz before the status check; V-06/P0-08 verdict CONFIRMED) |

*TD-07/08/09 live in §17 with the rest of the TD register; TD-04 = INT-01 (xref). The TD numbering keeps the `G#` references in [authentication-security/authorization-rules.md §3](authentication-security/authorization-rules.md#3-verified-object-level-gaps-idor-style) stable.*

---

## 2. WebSocket & Chat (WS, CHAT)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| WS-01 | SECURITY | Critical | ✅ | **FIXED (EB-19, contract verified on real sockets by P2-05 / EB-39):** room authorization enforced on both STOMP surfaces — SUBSCRIBE `/topic/room/{id}` runs the D-14 READ rule (mentorship enrollment or the owning mentor), SEND `/app/chat/{roomId}` the D-14 WRITE rule (explicit `ChatRoomMember` row or the room's owning mentor); violated frames are DROPPED and the authored error is pushed to the violator's `/user/{email}/queue/errors` (no STOMP ERROR frame is emitted — the async inbound channel swallows interceptor exceptions, proven wire-level by the EB-39 matrix); `saveMessage` re-checks the WRITE rule before persisting (defense in depth). Rules centralized in `service/chat/ChatAuthorization` | branch `feat/p1-12-ws-room-authorization`; probes `WSRoomAuthorizationProbesTest` (10) + socket matrix `WSAuthMatrixIntegrationTest` (9, EB-39); full suite 379 green; see ISSUE-MAPPING WS-01 | (resolved in EB-19) |
| WS-02 | TECHNICAL DEBT | Low | ✅ | **FIXED (EB-19):** dead code removed — the never-registered `JwtHandshakeInterceptor` deleted (channel CONNECT is the single WS auth path; SockJS fallback transports cannot authenticate, the FE must use the native WebSocket transport), `connectedUsers` remnants deleted, DISCONNECT logs via SLF4J instead of `System.out` | branch `feat/p1-12-ws-room-authorization`; see ISSUE-MAPPING WS-02 | (resolved in EB-19) |
| WS-03 | PERFORMANCE | Low | ✅ | Unbounded `size` on chat/notification message pagination (`PageRequest.of(0, size)` from client) | `ChatMessageService:85`, `ConversationService:86`, notification reads |
| CHAT-01 | RELIABILITY | Medium | ✅ | Room message save failure pushes an error string and **returns null** (client receives empty acknowledgment; no STOMP ERROR frame) | `ChatMessageService:43-51` |
| CHAT-02 | SECURITY | Critical | ✅ | **FIXED (EB-20 / P1-13):** History read requires conversation participant (student or mentor of pair) or ROLE_ADMIN; strangers receive 403 Forbidden without ID enumeration oracle | `ConversationRestControllers.getMessages`, `ConversationService.getConversationMessages`; probe `ConversationProbesTest.g2_` |
| CHAT-03 | SECURITY | High | ✅ | **FIXED (EB-20 / P1-13):** Private-message edit and delete enforce sender ownership; foreigners receive 403 Forbidden | `ConversationService.editMessage`, `ConversationService.deleteMessage`; probe `ConversationProbesTest.g3_` |
| CHAT-04 | SECURITY | High | ✅ | **FIXED (EB-20 / P1-13):** Server-derived event routing: edit/delete/send push events to genuine conversation partner resolved server-side from conversation record; client recipientEmail payload ignored | `ConversationRestControllers`, `ConversationWebSocketControllers`; probe `ConversationProbesTest.g3_senderEditsOwnMessage` |
| CHAT-05 | DATA INTEGRITY | Medium | ✅ | Duplicate-conversation race: get-or-create without transaction or unique constraint on (user1,user2) | `Conversation` entity; `ConversationService:63-66,162-173` |
| CHAT-06 | BUG | Low | ✅ | Edit/delete broadcasts address the **path-variable** room, not the message's actual room | `chatRestControllers:199-202,224-227` |
| CHAT-07 | BUG | Medium | ✅ | Read receipts: room messages have no flag at all; private `Message.seen` never set true anywhere → no unread mechanism in chat | `entity/chat/*`; grep `setSeen` = 1 (declaration) |
| CHAT-08 | TECHNICAL DEBT | Low | ⚠️ | `String content` STOMP binding (no `@Payload`) — JSON clients may persist raw JSON as message text; needs FE confirmation | `ChatWebSocketController:19-37` |

---

## 3. Mentorship domain (MSH)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| MSH-01 | SECURITY | High | ✅ | **FIXED (EB-22 / P1-15):** DRAFT mentorships confined to owner-mentor + ADMIN on every read surface (D-02 default (a)) — detail + student overview 404 for everyone else (no existence/status leak), public paged overview excludes DRAFT (`findAllByStatusNot`), explore/categories/top-mentorships/recommendations verified ACTIVE-only, join rejected on DRAFT already | `mentorShipServiceI#getMentorshipById`; `MentorshipOverviewService#getMentorshipWithEnrollmentStatus`; `MentorShipRepository#findAllByStatusNot`; `ContentReadGuard#isOwnerMentorOrAdmin`; probes `MentorshipDraftConfinementProbesTest` (8, EB-22); merged via PR #51 |
| MSH-02 | SECURITY | High | ✅ | Unguarded reads: any authenticated user can list any mentorship's tasks (incl. DRAFT, attachment paths) and read every lecture of any week/mentorship (`/mid/get-tasks`, `/lectures/**` GETs) | `mentorShipServiceI.getMentorShipTasks:239-249`; `LectureService:95-119` |
| MSH-03 | SECURITY | Medium | ✅ | Leaderboard enrollment guard **commented out** — any authenticated user sees any mentorship leaderboard | `MentorshipLeaderboardService:24-28` |
| MSH-04 | DATA INTEGRITY | Medium | ✅ | Enrollment dedupe protected only incidentally by the `TotalPoints` unique — concurrent join fails as 500 instead of "already enrolled"; `enrollments` lacks a unique constraint | `Enrollment` entity index (non-unique); `joinMentorship:387-441` |
| MSH-05 | RELIABILITY | Medium | ✅ | Fresh deployments cannot enroll: no default commission row → `globalLogicEx("Commission rate not set")` | `CommissionService` (latest-row lookup) |
| MSH-06 | SECURITY | Medium | ✅ | Cover upload trusts client Content-Type; extension taken from multipart filename without whitelist/sanitization; no magic-byte validation; failure window leaves broken `coverImageUrl` (old file deleted first) | `mentorShipServiceI.uploadCoverImage:340-369`; `ImageStorageService.saveCoverImage:21-52` |
| MSH-07 | BUG | Low | ✅ | Explore filters price on **base** price while displaying `priceAfterDiscount` → results inconsistent with UI | `MentorShipRepository.searchMentorShips:97,112-113` |
| MSH-08 | PERFORMANCE | Low | ✅ | N+1 on mentorship listing (`whatWillLearn` lazy per row) + in-memory pagination in homepage upcoming-items aggregator | repo `@EntityGraph({"tags"})` only; `HomePageService.getUpcomingItemsByMentorship:161-219` |
| MSH-09 | SECURITY | Medium | ✅ | Public reviews endpoint exposes reviewer emails + profile images | `MentorshipReviewProjection`; permitAll reviews route |
| MSH-10 | RELIABILITY | Medium | ✅ | Completion side effects (certificates + fan-out notifications) inside the status TX: rollback ghosting, I/O entanglement | `mentorShipServiceI.updateMentorShipStatus:253-319` |
| MSH-11 | SECURITY | Medium | ✅ | **FIXED (EB-24 / P1-17):** homepage progress endpoint now requires the caller's own enrollment (`existsByMentorShip_IdAndStudent_Email`) → non-enrolled 403, no curriculum-structure/composition leak, nonexistent ids 403 (no existence oracle); overview internal call already enrollment-gated (defense in depth unchanged) | `HomePageService.getStudentMentorshipProgress`; probes `HomepageProgressEnrollmentProbesTest` (5, EB-24) |
| MSH-12 | PERFORMANCE | Medium | ✅ | **CONFIRMED (EB-3 / P0-05, 2026-11-01 — V-03 POC, fix pending P4-10):** `:keyword` is bound RAW into `LIKE '%kw%'` across 5 columns with no `ESCAPE` clause — from the PUBLIC explore route, anonymous match-all payloads (`%`, `%%`, `%_`, `_%`, `__`) return the full ACTIVE candidate set (keyword destroyed as a selector; DRAFT confinement unaffected by wildcards) and `%`/`_` inside an otherwise-literal keyword silently widen the match (sibling rows impossible to isolate) | `searchMentorShips:104-109`; probes `security/probes/MentorshipExploreWildcardPocTest` (2) |

---

## 4. Learning content (LEARN, QZ)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| LEARN-01 | SECURITY | Critical | ✅ | `GET /api/v1/my-answers/{quizId}` returns `correctAnswer` for **every question before submission** → trivial perfect scores (defeats auto-grading) | `QuizSubmissionServiceImpl.getStudentQuizReview:196-247` |
| LEARN-02 | SECURITY | High | ✅ | Task/project detail + mentorship-filter reads unguarded (DRAFT content, attachment paths, statistics exposed); task-student view lacks enrollment check (project twin has it) | `TaskServiceImpl#getTaskById`; `TaskController` GETs; `findTaskWithSubmission:36-64` |
| LEARN-03 | SECURITY | High | ✅ | Generic `GET /files?filePath=` streams any uploads path without ownership (traversal itself blocked) — *(xref FILE-01 row)* | `FileController#serveFile:114-136` |
| LEARN-04 | BUG | Medium | ✅ | Task statistics: `pendingReview = totalStudents − submissionsPage.getTotalPages()` (page count, not submission count); project twin correct | `TaskServiceImpl#getTaskStatistics:272` |
| LEARN-05 | BUG | Medium | ✅ | Late-submission asymmetry: late **first** submissions accepted (gradeable full points); only late *re*-submissions rejected; deadline never blocks grading | `TaskSubmissionServiceImpl#submit:114-116`; `ProjectSubmissionServiceImpl#submit:88-90` |
| LEARN-06 | DATA INTEGRITY | Medium | ✅ | Points not reconciled with later changes: mentor can edit `points`/deadlines after grading (no recalc), content deletion never revokes points, resubmission leaves stale `pointsApplied` window | `updateTask/updateProject`; `deleteTask/deleteProject`; `submit` reset logic |
| LEARN-07 | BUG | Medium | ✅ | "Opened" quizzes count as submitted in My-Learning/progress and averageScore (attempt row born on first question GET) | `QuestionServiceImp:98-108`; `EnrollmentRepository#findAllEnrolledMentorshipsWithProgress:322-323` |
| LEARN-08 | BUG | High | ✅ | `passPoints` dead logic; **no pass/unlock mechanism anywhere** (docs/books imply checkpoint gating) — grep `unlock`=0 | `Task.passPoints` unused; tree-wide grep |
| LEARN-09 | BUG | Low | ✅ | `rawScore` stores max score; quiz points truncated `(int)`; NPE-shaped branches on zero-snapshot quiz stats | grade paths; `getAllSubmissionsByQuiz:283-291` |
| LEARN-10 | RELIABILITY | Medium | ✅ | Quizzes never hard-close (−1pt/min soft penalty only); promised `@Scheduled` auto-close doesn't exist | `QuizSubmissionServiceImpl:84-117` (unused import L27) |
| QZ-06 | SECURITY | Medium | ✅ | **FIXED (EB-26 / P1-19):** `updateQuestion` now validates the **target quiz's** ownership (`isMentorOwnQuiz` on `dto.quizId`) BEFORE the DRAFT-status check (no foreign-status oracle) — cross-mentor re-parent → 403, self-re-parent → 200; V-06/P0-08 verdict CONFIRMED (two-mentor probe) | `QuestionServiceImp#updateQuestion`; `QuestionReparentProbeTest` (G15/G15b, flipped EB-26) |

---

## 5. Users & profiles (USR)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| USR-01 | BUG | Medium | ✅ | Mentor→student projects endpoint drops the `status` filter param (ignored silently) | `ProfileService#getStudentProjects` |
| USR-02 | SECURITY | Low | ✅ | User-identifying surfaces (leaderboards, listings) expose emails beyond necessary scope — *(mostly via MSH-03/09; this row tracks read-surface breadth under prod rules)* | projections incl. emails |
| USR-03 | BUG | Low | ✅ | Dashboard projection quirks: `MentorStatsProjection.totalBadges` hardcoded 0; admin "students" counts enrollments (double counts repeat students) | `repository/users/*`; dashboard queries |
| USR-04 | TECHNICAL DEBT | Low | ✅ | Profile compositions hardcode `PageRequest.of(0,10)` caps (data silently cut off) | StudentProfileController internals |
| USR-05 *(xref)* | SECURITY | Medium | ✅ | = NOTIF-04 (single "newest admin" targeting) | – |

---

## 6. Gamification (GAME)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| GAME-01 *(xref)* | SECURITY | High | ✅ | = MSH-03 + G11 combined exposure (points/badges of any mentorship readable) | – |
| GAME-02 | SECURITY | Medium | ✅ | **FIXED (EB-26 / P1-19):** award reads scoped — `/badge-awards/student/{id}` had **zero scoping** (any authenticated id enumeration) and is now confined to self/ADMIN/mentor-of-enrollment; `/mentorship/{id}` + `/badge/{id}` were already owner-mentor-only (403 for others); badge **catalog** GETs stay open by plan (title/category/description/points — no personal data) | `BadgeAwardService#getAwardsByStudent`; probes `BadgeAwardScopingProbesTest` (8, EB-26) + G11 flip in `FileNotifyBadgeProbesTest` |
| GAME-03 | DATA INTEGRITY | Medium | ✅ | Badge point edits bulk-update all awardees bypassing lock + 0-clamp (totals can go negative; inconsistent with `applyDelta`) | `TotalPointsRepository.updatePointsForBadgeAwards:137-150`; contrast `applyDelta` |
| GAME-04 | RELIABILITY | Low | ✅ | 10-badges-per-mentorship cap is read-then-insert (racy) | `BadgeService#createBadge` |
| GAME-05 | TECHNICAL DEBT | Low | ✅ | Inconsistent accounting model (single-user clamp+lock vs bulk `@Modifying`) documented as accepted quirk | same as GAME-03 files |

---

## 7. Live sessions (LS)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| LS-01 | SECURITY | High | ✅ | Jitsi rooms are **public** `meet.jit.si` URLs with no JWT/auth (docs claim otherwise — DOC-01). Backend governs URL *distribution* only | `service/livesession/JitsiService` (15 lines); grep: no jitsi config |
| LS-02 | BUG | Medium | ✅ | Attendance math assumptions: 5-min snapshot grid, `snapshotless end queues everyone` Absent (0 pts); percentages can exceed 100; constants hardcoded (75%, +5 pts) | `LiveSessionServiceImp:394-481` |
| LS-03 | RELIABILITY | Medium | ✅ | No scheduled lifecycle — sessions can sit LIVE/ENDED forever if mentor forgets to end | repo-wide `@Scheduled` = OTP cleanup only |
| LS-04 | PERFORMANCE | Low | ✅ | Attendance saves in per-row loop; per-student notifications loop (N+1 + push storm) | `recordSnapshot`; `endSession` fan-out |
| LS-05 | TECHNICAL DEBT | Low | ✅ | Missing `@Transactional` on start/snapshot operations (inconsistent style) | `LiveSessionServiceImp` |

---

## 8. Certificates (CERT)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| CERT-01 | DOCUMENTATION | High | ✅ | README/books claim iText-rendered PDF certificates; **no rendering path exists** (iText used only for admin badge PDFs; `certificate-template.html` unreferenced) | grep `certificate-template|PdfGenerator` usage map |
| CERT-02 | TECHNICAL DEBT | Medium | ✅ | Rank surfaced without score/context; tie-break = `students.id ASC` (deterministic but arbitrary) | `CertificateService:44-94` |
| CERT-03 | PERFORMANCE | Low | ✅ | Leaderboard fetched **unpaged** for issuance (large-cohort memory risk) | `findLeaderboardByMentorshipId(Pageable.unpaged())` |
| CERT-04 | DATA INTEGRITY | Medium | ✅ | Badges remain editable on COMPLETED mentorships → points/bulk ledger change post-freeze while certificates stay frozen | `BadgeService#updateBadge` (no status check) |

---

## 9. Notifications & platform mail (NOTIF, MAIL)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| NOTIF-01 | SECURITY | High | ✅ | **DELETED (EB-18 / P1-11):** Open `/api/v1/notifications/send` deleted per Decision D-05 (returns 404) | `NotificationController` |
| NOTIF-02 | BUG | Medium | ✅ | Contact reply/notification path throws 404 for unregistered (guest) sender emails | `NotificationService.sendToUserByEmail` + `ContactMessageService` |
| NOTIF-03 | RELIABILITY | Medium | ✅ | STOMP pushes execute **pre-commit** (ghost notifications on rollback); inconsistent with admin-badge flow's afterCommit email | `NotificationService:94-98` vs `AdminBadgeService:65-76` |
| NOTIF-04 | SECURITY | Medium | ✅ | All admin-targeted pushes route to only the **newest** admin (`findTopByOrderByCreatedAtDesc`); no true broadcast | `NotificationService#sendToAdmin:149` |
| NOTIF-05 | TECHNICAL DEBT | Low | ✅ | Unused `NotificationProjection`; no email/push channel; unbounded page sizes | repo/service grep |
| MAIL-01 | SECURITY | High | ✅ | **DELETED (EB-18 / P1-11):** `POST /send` (`PlatformMailController` & `MailRequest`) deleted per Decision D-05 (returns 404) | `FileNotifyBadgeProbesTest.g10_platformMailSendEndpointDeleted` |

---

## 10. File storage (FILE)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| FILE-01 | SECURITY | Critical | ✅ | `/uploads/**` fully public (incl. student submissions) **and** `GET /files?filePath=` lets any authenticated user download any uploads path without ownership | `ProjectSecurityProdconfig:79-97` (+`/uploads/**` twice); `FileController#serveFile:114-136` |
| FILE-02 | SECURITY | High | ✅ | Image uploads (covers/avatars/chatroom) trust the client `Content-Type` header — no Tika/magic bytes (contrast: submission path) | `ImageStorageService`; contrast `FileStorageService:211-254` |
| FILE-03 | SECURITY | Medium | ⚠️ | `saveCoverImage` takes extension substring from multipart filename without sanitization/whitelist (traversal-suspect if separators sneak in; needs runtime confirmation) | `ImageStorageService.saveCoverImage:29-39` |
| FILE-04 | SECURITY | Medium | ✅ | Zip allowlist accepts generic `application/octet-stream` (Tika match loosens for zip → extension spoof possible for archives) | `FileStorageService` MIME map |
| FILE-05 | RELIABILITY | Medium | ✅ | Old-file-deleted-before-new-committed in replace flows (covers/profile) → broken URLs on failure | `uploadCoverImage:361`; profile image paths |
| FILE-06 | TECHNICAL DEBT | Medium | ✅ | Real uploaded user files committed to the repo under `uploads/` (privacy + hygiene) | `uploads/` contents in git |
| FILE-07 | RELIABILITY | Medium | ✅ | No Docker volume for uploads → files lost on redeploy | `Dockerfile:11`; no compose/volumes |

---

## 11. Persistence (DB)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| DB-01 | DEPLOYMENT | High | ✅ | `ddl-auto=update` **in prod** + no migrations tool → uncontrolled schema drift, no rollback, destructive entity edits deploy silently | both profiles' properties |
| DB-02 | TECHNICAL DEBT | Medium | ✅ | `scripts/script.sql` is inert (no `spring.sql.init`/Docker wiring); no schema source of truth beyond entities | resources dir; grep config refs = 0 |
| DB-03 | DATA INTEGRITY | Medium | ✅ | Soft-delete (`deleted`) not applied in read queries (leaderboards/reviews/chat can surface deleted users) | query layers across repos |
| DB-04 | DATA INTEGRITY | Medium | ✅ | Missing unique constraints: enrollments, mentorship reviews, conversations, session-attendance pairs | entity definitions (§ indexing doc) |
| DB-05 | RELIABILITY | Medium | ✅ | `@Transactional` omissions on multi-step flows (`WeekService.deleteWeek`, `startLiveSession`, `recordSnapshot`) | cited methods |
| DB-06 | TECHNICAL DEBT | Low | ✅ | OSIV reliance for lazy navigation (breaks if disabled; sources several N+1s) | quiz/dashboard read paths |
| DB-07 | PERFORMANCE | Low | ✅ | Row-by-row persistence loops (attendance snapshots, per-student fans) instead of batching | cited services |

---

## 12. API layer (API)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| API-01 | TECHNICAL DEBT | Low | ✅ | Envelope inconsistency: `SimpleResponse` vs typed DTOs vs `PageResponse<T>`; no uniform errors contract | controller survey |
| API-02 | PERFORMANCE | Medium | ✅ | Unbounded `size` params; inconsistent defaults (5/6/10); one in-memory paginating aggregator | endpoints doc §2 |
| API-03 | TECHNICAL DEBT | Low | ✅ | Path-version drift/email-as-identifier paths (`/login-api`, `/files`, `/admin`, absolute badge paths) | route survey |
| API-05 | BUG | Medium | ✅ | Jackson `fail-on-unknown-properties` true **prod only** → unknown-field 400 in prod, silent ignore in dev (contract drift) | properties delta |
| API-04 *(xref)* | SECURITY | High | ✅ | **FIXED (EB-16):** = SEL-08 — admin response credential leaks resolved (identity-only DTO) | see ISSUE-MAPPING SEL-08 | (resolved in EB-16) |

---

## 13. Integrations (INT)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| INT-01 | TECHNICAL DEBT | Low | ✅ | Unused JavaFX dependency (`org.openjfx:javafx-base:20`) | `pom.xml` + zero imports |
| INT-02 | RELIABILITY | Medium | ✅ | No email-delivery observability: `@Async` failures swallowed/logged only; no retry/outbox; no bounce metrics | `EmailService` |
| INT-03 | TECHNICAL DEBT | Low | ✅ | Dead template `certificate-template.html` (unreferenced) | template grep |

---

## 14. Deployment & ops (DEP)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| DEP-01 | DEPLOYMENT | High | ✅ | **No CI/CD** — `.github/` has no workflows; images built locally with `-DskipTests` | `.github` glob = empty; `Dockerfile:6` |
| DEP-02 | RELIABILITY | High | ✅ | No health checks/actuator; no logback/logging config; stale single-node assumptions (simple broker) | pom + Dockerfile; broker config |
| DEP-03 | DEPLOYMENT | High | ✅ | No volume/managed storage for `uploads/` (data loss on container replacement) | Dockerfile:11 |
| DEP-04 | DEPLOYMENT | Medium | ✅ | `show-sql=true` + `ddl-auto=update` in prod profile | properties |
| DEP-05 *(xref)* | SECURITY | — | ✅ | = SEL-01 deployment dimension (JWT secret never wired; not declared in env docs) | – |
| DEP-06 | TECHNICAL DEBT | Medium | ✅ | Committed runtime files (`uploads/`) — cleaning history needed | git status |
| DEP-07 | DEPLOYMENT | Medium | ✅ | No compose/k8s manifest, no `.env.example`; secret-management story absent | repo root |
| DEP-08 | RELIABILITY | Low | ✅ | No backup/restore runbook; DB-side cleanup jobs only for OTP | scripts/properties |

---

## 15. Testing (TST)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| TST-01 | TEST GAP | High | ✅ | One context-load smoke test only; **zero** functional/integration coverage across all domains | `src/test/**` |
| TST-02 | TEST GAP | Medium | ✅ | No test profile/Testcontainers/fixtures — smoke test depends on live env | no `application-test.properties` |
| TST-03 | TEST GAP | Medium | ✅ | Docker build explicitly `-DskipTests` — zero verification on shipped images | `Dockerfile:6` |
| TST-04 | TEST GAP | Low | ✅ | No static analysis / coverage tooling | pom |

---

## 16. Documentation (DOC)

| ID | Cat | Sev | Status | Issue | Evidence |
|---|---|---|---|---|---|
| DOC-01 | DOCUMENTATION | Medium | ✅ | Root README + graduation books claim features that don't exist (Jitsi JWT rooms, PDF certificates, iText certificates, "28+ features") — misleads reviewers/stakeholders | README.md; three book .md files; verified absences (LS-01, CERT-01) |
| DOC-02 | DOCUMENTATION | Low | ✅ | Three overlapping legacy books (65KB/151KB/826KB) at repo root — no maintained engineering docs before `mds/` | repo root listing |
| DOC-03 | DOCUMENTATION | Low | ✅ | Old `mds/` audits scattered at `mds/` root domain-misfiled — **RESOLVED** by consolidation (moved into domain dirs, links updated) | `mds/**` tree |

---

## 17. Naming, dead code & misc debt (TD)

| ID | Cat | Sev | Status | Issue |
|---|---|---|---|---|
| TD-01 | TECHNICAL DEBT | Low | ✅ | Naming inversions: `service/auth/LoginService` is the **interface**, `LoginServiceI` the impl; `mentorShipServiceI`; lowercase `chatRestControllers`, `ConversationRestControllers` |
| TD-02 | TECHNICAL DEBT | Low | ✅ | Two utility packages `utils` + `util` (Constants vs FileResponseBuilder) |
| TD-03 | TECHNICAL DEBT | Low | ✅ | Dead code inventory: `JwtTokenGeneratorFilter` (=missing `/test-login` route), `DirectMessageRequest`, `NotificationProjection`, `ChatRoomRepository` injection in interceptor (method doesn't exist), `ChatMessageRepository.findSenderInfo`, `MessageRepository.findByConversationIdOrderBySentAtDesc`, `CommissionService.calculateMentorPayout`, unused `@Scheduled` imports |
| TD-04 *(xref)* | TECHNICAL DEBT | Low | ✅ | = INT-01 (JavaFX dep) |
| TD-05 | TECHNICAL DEBT | Low | ✅ | `System.out.println` debug prints in production paths (`JwtChannelInterceptor:153`, `RolesSeeder:24`, `QuizSubmissionServiceImpl:97`) |
| TD-06 | TECHNICAL DEBT | Low | ✅ | `features/roadmap/**` empty skeleton (zero files, zero references) — implement or delete |
| TD-07 | TECHNICAL DEBT | Medium | ✅ | Room creation ownership gap: any mentor can create rooms in anyone's mentorship (`ChatRoomService.createRoom:43-79`) |
| TD-08 | TECHNICAL DEBT | Low | ✅ | Three inconsistent "membership" semantics — enrollment-proxy REST vs room-join vs commented WS checks (`AuthorizationRepository:82-93` vs `EnrollmentRepository:178-191`) |
| TD-09 | TECHNICAL DEBT | Low | ✅ | Profile-image folder names mismatch security-config sub-paths; coverage only via the catch-all `/uploads/**` rule (`ImageStorageService.saveImage` folders) |

---

## 18. Cross-cutting rollups (ARCH, reference rows)

| Rollup | Canonical rows | Statement |
|---|---|---|
| ARCH-001 | SEL-02 | Dev profile wide open |
| ARCH-002 | WS-01 | WS surface unguarded |
| ARCH-003 | SEL-01 | Secret handling flaw |
| ARCH-004 | DEP-02 + in-memory broker | Single-node scale ceiling; only OTP scheduler exists |
| ARCH-005 | TD-01 + TD-02 | Conventions/naming debt |
| ARCH-006 | DOC-01 | Docs-vs-code parity debt |
| ARCH-007 | TD-06 | Roadmap skeleton |

---

## 19. Disconfirmed suspicions (checked, absent — keep as regression notes)

- ❌ `System.currentTimeMillis()` for message timestamps — zero occurrences (DB-generated timestamps used).
- ❌ WS handshake currently reads/leaks tokens — interceptor never registered.
- ❌ Cross-mentor grading — grading ownership verified correct (task/project/quiz paths).
- ❌ Chat 3-way notification spam — notification Venn loops bounded (each fan-out single row + relations).
- ❌ "_plaintext password during the reset execution" — reset stores only BCrypt; reset request carries the new password transiently (expected).

## 20. Severity tally (unique rows, xrefs excluded)

| Severity | Rows |
|---|---|
| Critical | 7 |
| High | 21 |
| Medium | 46 |
| Low | 24 |
| **Total** | **≈98** |

Priority execution order (dependency-aware) is maintained in [deployment/production-readiness.md §5](deployment/production-readiness.md#5-suggested-stabilization-phase-order-dependency-aware).
