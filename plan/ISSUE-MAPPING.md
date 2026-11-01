# Issue Mapping — Canonical Issues → Plan Tasks (Traceability Index)

Live tracker. Update statuses **in this file** per execution ([`TEAM-WORKFLOW §6`](TEAM-WORKFLOW.md#6-sync-rules-with-the-audit-registry)); `mds/ISSUES.md` rows stay untouched (they are audit truth). Statuses: `Planned → Ready → In Progress → Implemented-await-verification → Verified`, or `Deferred by decision` / `Blocked`.

**Disposition legend:** T = implementation task planned · V→T = verification task first, then implementation · D→T = decision-gated, proceeds on documented default · DEF = deferred by decision · RES = resolved during documentation phase.

> **Decision bulletin (2026-10-25 — read before acting on any row):**
> - **D-05 decided → DELETE:** dispositions for `SEL-10`, `NOTIF-01` (=SEL-10), `MAIL-01` become **delete, not gate** — executed via P1-11 (re-scoped, Jira EB-18) + addendum **[ADD-04]** (Jira EB-84).
> - **D-07 + D-25 decided → Supabase Storage (S3):** rows touching `FILE-01` (P1-21, Jira EB-28), `FILE-07`/`DEP-03` (P6-05, P6-07) and **D-25** change mechanics: local-disk serving is replaced by Supabase Storage with bucket policies + ownership checks (addendum **[ADD-03]**, Jira EB-83). P6-07 is **superseded**.
> - **D-22 decided → Supabase Postgres:** assumption **A-4 (MySQL 8) superseded**; addenda **[ADD-01]** (EB-81) and **[ADD-02]** (EB-82) precede `P3-01`; the Flyway baseline must be captured on Postgres. All DB-family rows keep their logical scope (constraints/transactions/soft-delete unchanged), only the migration medium changed.
> - **D-14 decided → fix approved:** `WS-01` disposition unblocked; `P1-12` proceeds (Jira EB-19).
> - **D-16 decided → Zoom replaces Jitsi:** `LS-01` disposition changes from "keep public + docs truth" to platform swap; `P4-17` re-scoped to Zoom integration (Jira EB-66) with addendum **[ADD-05]** (EB-85) provisioning the Zoom app. DOC-01 claims are corrected toward Zoom at P5-05 (EB-71).
> Row-level `Status` cells below stay `Planned` until implementation + verification evidence exists (sync rule, TEAM-WORKFLOW §6).

| Issue | Sev | Task(s) | Phase | Domain | Disposition | Status |
|---|---|---|---|---|---|---|
| **SEL-01** | Crit | P1-01 | 1 | auth | T + D-22 ordering note | **Verified** (merged to `main` 2026-10-28; tests 7/7; fail-fast boot live-proven) |
| **SEL-02** | Crit | P1-02 | 1 | auth | T | **Verified** (merged to `main` 2026-10-28, commit `af50e58`; boot matrix 4/4 — `SecurityProfileBootMatrixTest`; Jira EB-9 = Done) |
| **SEL-03** | Crit | P1-08 → P5-03 | 1,5 | auth | T (two-stage) → **core half FIXED** | **EB-15 (2026-10-30, merge `4529aeb`, PR #31, CI green):** throttling + lockout + enumeration unification all live — `AuthRateLimitFilter` 8/1min on the 6 public credential buckets (key `ip|bucket|account`, 10-min TTL sweep), `LoginLockoutService` 5 consecutive failures → 5-min `ERR_ACCOUNT_LOCKED`, all five failure kinds fold to one canonical 401 `ERR_INVALID_CREDENTIALS` byte-identical body (R4 probe-pinned), dynamic `Retry-After` on 429s; FE contract in EF-3. P5-03 keeps the platform-wide extension (public unauthenticated routes at scale) |
| **SEL-04** | High | P1-07 | 1 | auth | T | **Verified** (merged to `main` via PR #30, `52452a0`, 2026-10-29, Team Lead-approved; Linux/JDK 21 CI 187/187 — [run 40966140701](https://github.com/MooRaa86/EduNest/actions/runs/40966140701); one-time reset credential, digest-only storage, atomic consumption under user lock; Jira EB-14 = Done). Pre-deployment follow-ups remain: frontend adoption of `resetToken`, target-database `otp.otp_code` capacity/lock verification, mixed-version rollout coordination (see authentication-flow.md). |
| **SEL-05** | High | P1-06 | 1 | auth | T | **Verified** (merged to `main` via PR #19, `d3a14a2`, 2026-10-28; 376-line `OtpTypeIsolationIntegrationTest` green in CI; Jira EB-13 = Done) |
| **SEL-06** | High | P1-27 (cond.) | 1 | auth | D→T (D-01) | **Documented — EB-34 (2026-11-01):** D-01 outcome "(a) narrowed — stateless JWT stands for Sprint 1" codified, **no build**: refresh tokens + real session/revocation model formally **carried to the Sprint 2 backlog** (tracked only by the D-01 decision record; no dedicated Jira issue exists yet). Docs: `mds/authentication-security/security-configuration.md` §3c + `mds/api/endpoints.md` (§1 session-invalidation note + §2 state-effect rows). Jira EB-34 closure pending Omar's PR review. |
| **SEL-07** | Med | P1-25 | 1 | auth | T | EB-32 implementation in progress: login and verification DTO validation; resend email parameter validation; legacy password-match path retired. Verification, review and merge pending. |
| **SEL-08** | High | P1-09 | 1 | auth | T (+API-04) | **Implemented — EB-16** (branch `feat/p1-09-admin-bootstrap-protection`; review/merge pending): bootstrap response no longer echoes plaintext; admin list no longer exports hashes; one-shot bootstrap guard synchronised (`400 ERR_BUSINESS_RULE "Admin bootstrap is no longer available."` once any admin exists, audited text); last-admin delete guard closes the bootstrap re-open hole; `deleteAdmin` made transactional (was a bare 500); probe suite `AdminBootstrapProbesTest` (7 probes). |
| **API-04** | High | P1-09 | 1 | auth | = SEL-08 | **Implemented — same scope as SEL-08 above (EB-16).** Field removed from shared admin response DTO = FE-visible contract change; FE note included in docs (`endpoints.md` admin rows). |
| **SEL-09** | Med | P1-10 | 1 | auth | T | **Implemented — EB-17 (2026-11-01):** method-security sweep — `AdminBadgeController` class-level `hasRole('ADMIN')` (URL rules unchanged, matches the other admin controllers); dashboard services completed — `MentorshipDashboardService#getStats`, `DashboardService#getSalesChartData`/`getStudents` now carry the `hasRole('MENTOR')` gate their siblings had; role-matrix probes in `MethodSecuritySweepProbesTest`; merged via [PR #59](https://github.com/MooRaa86/EduNest/pull/59) (merge `8b5f2f9`, 2026-11-01) — **Verified**, Jira EB-17 = Done |
| **SEL-10** | High | P1-11 | 1 | auth | D→T (D-05) | **Implemented (Deleted) — EB-18** (2026-11-01): `POST /api/v1/notifications/send` deleted per Decision D-05; `FileNotifyBadgeProbesTest.g10_userPushesNotificationToAnyone` verifies 404 Not Found. |
| **SEL-11** | Med | P1-23 | 1 | auth | D→T (D-23) | **Implemented, awaiting verification** (branch `feat/p1-23-cors-allowlist`; Jira EB-30): wildcard CORS preserved per approved D-23 public contract, `allowCredentials` set to `false` in prod and dev chains, eliminates credentialed wildcard misconfiguration; `Authorization` exposed; verified by dedicated regression suite `CorsProbesTest` (5 probes green); PR pending lead review/merge. |
| **SEL-12** | Med | P1-04 | 1 | auth | T (+D-01 note) | EB-11 implementation in review: JWT always in Authorization header; body gated by compatibility property (default true pending FE migration); ProviderManager erases credentials. Merge, CI and FE/security review remain pending. |
| **SEL-13** | Med | — | — | auth | DEF (latent design) | Deferred |
| **SEL-14** | High | P1-05 | 1 | api | D→T (D-26) → **FIXED** | EB-12 (2026-10-29): unified `ApiError` envelope + `ApiErrorCode` catalog across all five producers; catch-all leak fixed (409+message → 500 sanitized + refId); explicit 403 for method-security `AccessDeniedException`; three 401 dialects → one writer; zero-leak probes E1–E6 in `ErrorShapeProbeTest` (flipped same-PR); D-26 fields map kept (nested under envelope) |
| **SEL-15** | Med | P0-04 → P1-24 | 0,1 | auth | V→T | Planned |
| **SEL-16** | Med | P1-03 | 1 | auth | T | **Implemented — EB-10** (2026-11-01): `PublicRoutesPolicy` single source of truth for all public routes (APIs + public upload folders); `JwtTokenValidatorFilter` catches invalid/stale/garbage JWTs and gracefully proceeds anonymously on public routes; protected routes continue to reject with 401 `ERR_INVALID_TOKEN`; dual-mode routes preserve authentication context when valid token is present; verified by 35 probes in `PublicRouteMatrixProbeTest`. |
| **WS-01** | Crit | P1-12 | 1 | ws | D→T (D-14) | **Implemented — EB-19** (branch `feat/p1-12-ws-room-authorization`; review/merge pending): `ChatAuthorization` enforces the D-14 READ rule on SUBSCRIBE and WRITE rule on SEND; `saveMessage` re-checks as defense in depth; violations dropped with explicit STOMP ERROR frame; probe suite `WSRoomAuthorizationProbesTest` (10) + V-01 re-seeded under the new contract; full suite 371 green |
| **WS-02** | Low | P1-12 | 1 | ws | T | **Implemented — EB-19** (same scope as WS-01 above): dead `JwtHandshakeInterceptor` deleted; `connectedUsers` remnants removed; DISCONNECT logging via SLF4J |
| **WS-03** | Low | P4-09 | 4 | api | T | Planned |
| **CHAT-01** | Med | P4-06 | 4 | ws | T | Planned |
| **CHAT-02** | Crit | P1-13 | 1 | ws | T | **Implemented — EB-20**: History read requires conversation participant or ROLE_ADMIN; strangers receive 403 Forbidden without ID enumeration oracle (`ConversationProbesTest.g2_`) |
| **CHAT-03** | High | P1-13 | 1 | ws | T | **Implemented — EB-20**: Private message edit and delete enforce sender ownership; foreigners receive 403 Forbidden (`ConversationProbesTest.g3_`) |
| **CHAT-04** | High | P1-13 | 1 | ws | T | **Implemented — EB-20**: Server-derived recipient event routing for edit, delete, and send; client-supplied recipientEmail ignored (`ConversationProbesTest.g3_senderEditsOwnMessage`) |
| **CHAT-05** | Med | P3-02 | 3 | database | T | Planned |
| **CHAT-06** | Low | P4-06 | 4 | ws | T | Planned |
| **CHAT-07** | Med | P4-07 (cond.) | 4 | ws | D→T (D-13) | Planned |
| **CHAT-08** | Low | P0-03 → P4-06 | 0,4 | ws | V→T (cond.) → FIXED | RESOLVED by EB-1 (2026-10-29, same-PR): mechanism confirmed by V-01 probe, then `@Payload ChatMessageRequest` DTO + blank guard shipped; objects now bind to pure text, raw strings rejected ([tasks#p0-03](phases/phase-00-planning-baseline/tasks.md#p0-03)) — P4-06 keeps CHAT-01/06 only |
| **MSH-01** | High | P1-15 | 1 | auth | D→T (D-02) | **Implemented — EB-22**: DRAFT mentorship confined to owner-mentor + ADMIN on every read surface (D-02 default (a)) — detail + student overview 404 for everyone else (no existence/status leak), public paged overview excludes DRAFT (`findAllByStatusNot`), explore/categories/top-mentorships/recommendations verified ACTIVE-only, join already rejected DRAFT; probes `MentorshipDraftConfinementProbesTest` (8) + G8/G8b flips in `LeaderboardDraftProbesTest`, full suite 414 green — pending review |
| **MSH-02** | High | P1-14 | 1 | learning | D→T (D-03) | EB-21 implementation: service-level course read guards (ContentReadGuard) merged to main (PR #38, commit 8175e28); content reads restricted to enrolled student, owning mentor or ADMIN; student drafts hidden; CI passed |
| **MSH-03** | Med | P1-16 | 1 | auth | D→T (D-04, amended Ruler B) | EB-23 implementation: leaderboard scoped to enrolled student, owner or ADMIN; rows = `studentFullName`, `studentEmail`, `studentProfileImageUrl`, `rank`, `totalPoints` (no `studentLevel`/badges); review and CI pending |
| **MSH-04** | Med | P3-02 | 3 | database | T | Planned |
| **MSH-05** | Med | P0-09 → P3-05 | 0,3 | database | V→T (D-17) | Planned (EB-7 2026-11-01: defect reproduced on fresh-DB rehearsal, `plan/phases/phase-00-planning-baseline/EB-7-FRESH-DB-REHEARSAL.md`; no seed exists yet, fix deferred to P3-05 pending D-17) |
| **MSH-06** | Med | P1-22 | 1 | file-storage | T | **Hardened - EB-29 (implementation + probe suite)**: stored extension derives from the detected content format, not the client filename; declared MIME cross-checked against the canonical set for the detected type; all nine replace flows now save the new file before deleting the old one so a rejected upload no longer destroys the previous file ([PR #33](https://github.com/MooRaa86/EduNest/pull/33); baseline [EB-4 matrix](phases/phase-00-planning-baseline/EB-4-UPLOAD-MATRIX.md)) |
| **MSH-07** | Low | P4-10 | 4 | learning | T (default: effective price) | Planned |
| **MSH-08** | Low | P4-10, P4-11 | 4 | learning | T | Planned |
| **MSH-09** | Med | P1-16 | 1 | auth | D→T (D-04) | Temporary public-review email and image compatibility exception Engineering Lead approved; frontend usage verification pending |
| **MSH-10** | Med | P4-03 | 4 | notifications | T | Planned |
| **MSH-11** | Med | P1-17 | 1 | auth | T | **Implemented — EB-24**: homepage progress (`GET /api/v1/homepage/progress/{id}`) requires the caller's own enrollment — non-enrolled STUDENT gets 403 (`AccessDeniedException`), no curriculum-composition leak, nonexistent ids 403 (no existence oracle); overview internal call already gated (unchanged); probes `HomepageProgressEnrollmentProbesTest` (5) + full suite 422 green — pending review |
| **MSH-12** | Med | P0-05 → P4-10 | 0,4 | learning | V→T | Planned |
| **LEARN-01** | Crit | P1-18 | 1 | learning | T | **Implemented — EB-25**: `getStudentQuizReview` (`/api/v1/my-answers/{quizId}`) populates `correctAnswer` only once the attempt is genuinely submitted (`QuizSubmission#score != null` — the same marker `submitQuizAnswers` uses as its "already submitted" guard; opening a quiz creates the row with `score == null`, LEARN-07); pre-submission the field is omitted from the JSON (`@JsonInclude(NON_NULL)` on `StudentQuizReviewDTO.correctAnswer`; mentor answer view `/answer/{studentId}/{quizId}` keeps full keys with the explicit ownership assert now pinned by `QuizAnswerKeyScopingProbesTest` (3 probes); merged via [PR #55](https://github.com/MooRaa86/EduNest/pull/55) (merge `1253d83`, 2026-11-01) — **Verified**, Jira EB-25 = Done |
| **LEARN-02** | High | P1-14 | 1 | learning | D→T (D-03) | EB-21 implementation: course-scoped task/project reads and caller-scoped submission views merged to main (PR #38, commit 8175e28); access restricted to enrolled student, owning mentor or ADMIN; CI passed |
| **LEARN-03** | High | P1-20 | 1 | file-storage | D→T (D-06) | EB-27 implements current-reference and ownership-checked generic access; Security/Lead review, CI and frontend compatibility remain pending. |
| **LEARN-04** | Med | P4-05 | 4 | learning | T | Planned |
| **LEARN-05** | Med | P4-04 | 4 | learning | D→T (D-08) | Planned |
| **LEARN-06** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **LEARN-07** | Med | P4-05 | 4 | learning | T | Planned |
| **LEARN-08** | High | P4-15 (cond.) | 4 | learning | D→T (D-10), DEF ok | Planned |
| **LEARN-09** | Low | P4-05 | 4 | learning | T | Planned |
| **LEARN-10** | Med | P4-04 | 4 | learning | D→T (D-08) | Planned |
| **QZ-06** | Med | P0-08 → P1-19 | 0,1 | learning | V→T | **V-06 verdict: CONFIRMED (EB-26, 2026-11-01)** — two-mentor probe `QuestionReparentProbeTest` pre-fix returned 200 (own question into a foreign mentor's quiz); code audit shows `updateQuestion` never validated the target quiz's ownership → **Implemented — EB-26**: target-quiz ownership enforced (`isMentorOwnQuiz`) before the DRAFT-status check (no foreign-status oracle); foreign re-parent 403, self re-parent 200 (G15/G15b flip); merged via [PR #53](https://github.com/MooRaa86/EduNest/pull/53) (squash `4bc8a52`) — verification-first loop closed by EB-6 (P0-08 sync); EB-6's sync merged via [PR #58](https://github.com/MooRaa86/EduNest/pull/58) (merge `4b07135`, 2026-11-01) — **Verified**, Jira EB-6 = Done |
| **USR-01** | Med | P4-09 | 4 | users | T | Planned |
| **USR-02** | Low | P1-16 | 1 | auth | D→T (D-04) | EB-23 addresses leaderboard and public review reviewer fields; broader public-profile/project surfaces require separate verification |
| **USR-03** | Low | P4-05 | 4 | users | T | Planned |
| **USR-04** | Low | P4-09 | 4 | users | T | Planned |
| **USR-05** | Med | P4-01 | 4 | notifications | = NOTIF-04 | Planned |
| **GAME-01** | High | P1-16 / P1-19 | 1 | gamification | = MSH-03+G11 | Leaderboard portion in EB-23; G11 badge-award portion **delivered in EB-26** (both now closed) |
| **GAME-02** | Med | P1-19 | 1 | gamification | T | **Implemented — EB-26**: `/badge-awards/student/{id}` (the unguarded surface — any authenticated user enumerated any student's awards by id) now confined to self/ADMIN/mentor-of-enrollment; `/mentorship/{id}` + `/badge/{id}` award reads already owner-mentor-only; badge catalog GETs stay open by plan (no personal data); probes `BadgeAwardScopingProbesTest` (8) + G11 flip — pending review |
| **GAME-03** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **GAME-04** | Low | P4-02 | 4 | gamification | T | Planned |
| **GAME-05** | Low | P4-02 | 4 | gamification | T (doc-in-code) | Planned |
| **LS-01** | High | P4-17 (cond.), P5-05 | 4,5 | live-sess | D→T (D-16), DEF ok | Planned |
| **LS-02** | Med | P4-08 | 4 | live-sess | D→T (D-15) | Planned |
| **LS-03** | Med | P4-08 | 4 | live-sess | D→T (D-15) | Planned |
| **LS-04** | Low | P4-12 | 4 | database | T | Planned |
| **LS-05** | Low | P4-08 | 4 | live-sess | T | Planned |
| **CERT-01** | High | P5-05 (+optional feature) | 5 | certificates | D→T (D-11), DEF ok | Planned |
| **CERT-02** | Med | P4-05 | 4 | certificates | T | Planned |
| **CERT-03** | Low | P4-05 | 4 | certificates | T | Planned |
| **CERT-04** | Med | P4-02 | 4 | gamification | D→T (D-09) | Planned |
| **NOTIF-01** | High | P1-11 | 1 | notifications | D→T (D-05) | **Implemented (Deleted) — EB-18** (same scope as SEL-10 above). |
| **NOTIF-02** | Med | P4-01 | 4 | notifications | T | Planned |
| **NOTIF-03** | Med | P4-01 | 4 | notifications | T | Planned |
| **NOTIF-04** | Med | P4-01 | 4 | notifications | D→T (D-12) | Planned |
| **NOTIF-05** | Low | P4-01 | 4 | notifications | T | Planned |
| **MAIL-01** | High | P1-11 | 1 | notifications | D→T (D-05) | **Implemented (Deleted) — EB-18** (2026-11-01): `PlatformMailController` (POST `/send`), `MailRequest`, and `general-mail.html` deleted per Decision D-05; `FileNotifyBadgeProbesTest.g10_platformMailSendEndpointDeleted` verifies 404 Not Found. |
| **FILE-01** | Crit | P1-21 + P1-20 | 1 | file-storage | D→T (D-07/D-06) | **In Progress** — EB-28 local-disk restrictions merged via [PR #23](https://github.com/MooRaa86/EduNest/pull/23), `171c6a1`; [JDK 21 CI](https://github.com/MooRaa86/EduNest/actions/runs/40956840871) passed 177 tests. One owner approval is recorded; distinct Security review, frontend and deployment acceptance are not evidenced. EB-27 and Supabase Storage EB-83 remain open; Lead/Security must clarify the D-07 acceptance boundary. |
| **FILE-02** | High | P1-22 | 1 | file-storage | T | **Hardened - EB-29 / EB-87 (implementation + probe suite)**: image surfaces no longer trust the client Content-Type — shared `UploadContentValidator` proves the bytes (magic + structural + JDK decode for jpg/png; structural webp), per-surface allowlists (covers/avatars jpg/png/webp, chatroom jpg/png), 5 MB limit, declared-MIME cross-check ([PR #33](https://github.com/MooRaa86/EduNest/pull/33); baseline [EB-4 matrix](phases/phase-00-planning-baseline/EB-4-UPLOAD-MATRIX.md)); EB-87 locks verdict: standard formats (JPEG, PNG, WebP) supported normally, GIF/BMP rejected with 400 ERR_BUSINESS_RULE, SVG prohibited, 5 MiB cap strictly verified everywhere vs 10 MiB files, replace safety regression-pinned across all surfaces |
| **FILE-03** | Med | P0-06 → P1-22 | 0,1 | file-storage | V→T | **Hardened - EB-29 (implementation + probe suite)**: stored extension is derived from the detected format — the client filename no longer reaches the path (`photo.jpg.exe` with real JPEG bytes normalizes to `{id}-EduNest-{uuid}.jpg`); double-extension/garbage payloads rejected ([PR #33](https://github.com/MooRaa86/EduNest/pull/33); baseline [EB-4 matrix](phases/phase-00-planning-baseline/EB-4-UPLOAD-MATRIX.md)) |
| **FILE-04** | Med | P0-06/P0-07 → P1-22 | 0,1 | file-storage | V→T | **Hardened - EB-29 [P1-22] (implementation + probe suite)**: the `.zip` allowlist entry `application/octet-stream` is removed — only `application/zip` / `application/x-zip-compressed` remain — and a stored `.zip` must additionally pass a structural central-directory parse (`UploadContentValidator#verifyZipIntegrity` on the temp file before it is moved); mislabeled/MIME-blocked attachments now answer **400 `ERR_BUSINESS_RULE`** (EB-12 envelope) instead of the sanitized 500. Rejections leave no file/`.tmp`/metadata behind; genuine-zip-named-`.pdf` and local-header-only archives rejected. Evidence basis: EB-5 endpoint verdict ([EB-5-ZIP-VERDICT](phases/phase-00-planning-baseline/EB-5-ZIP-VERDICT.md)), [EB-4 matrix](phases/phase-00-planning-baseline/EB-4-UPLOAD-MATRIX.md), probe suite `UploadValidationHardeningProbeTest` ([PR #33](https://github.com/MooRaa86/EduNest/pull/33); full-suite run 221/221 green on the PR branch). |
| **FILE-05** | Med | P3-03 | 3 | file-storage | T | Planned |
| **FILE-06** | Med | P6-06 | 6 | deployment | D→T (D-19) | Planned |
| **FILE-07** | Med | P6-05 (+P6-07 cond.) | 6 | deployment | T | Planned |
| **DB-01** | High | P3-01 | 3 | database | T (+DEP-04) | Planned |
| **DB-02** | Med | P3-01 | 3 | database | T | Planned |
| **DB-03** | Med | P3-04 | 3 | database | D→T (D-18) | Planned |
| **DB-04** | Med | P3-02 | 3 | database | T | Planned |
| **DB-05** | Med | P3-03 | 3 | database | T | Planned |
| **DB-06** | Low | P3-06 → P4-11 | 3,4 | database | T (+D-24) | Planned |
| **DB-07** | Low | P4-12 | 4 | database | T | Planned |
| **API-01** | Low | P4-16 (cond.) | 4 | api | D→T (D-20), DEF expected | Planned |
| **API-02** | Med | P4-09 | 4 | api | T | Planned |
| **API-03** | Low | P4-16 (cond.) | 4 | api | D→T (D-20), DEF expected | Planned |
| **API-04** | High | P1-09 | 1 | auth | = SEL-08 | **Implemented — same scope as SEL-08 above (EB-16).** Field removed from shared admin response DTO = FE-visible contract change; FE note included in docs (`endpoints.md` admin rows). |
| **API-05** | Med | P4-14 | 4 | api | T | Planned |
| **INT-01** | Low | P1-26 | 1 | integrations | T (+Agora/R11) | Planned |
| **INT-02** | Med | P4-13 | 4 | integrations | T | Planned |
| **INT-03** | Low | P1-26 | 1 | integrations | T | Planned |
| **DEP-01** | High | P2-07 → P6-04 | 2,6 | deployment | T | **P2-07 Verified** (workflow merged & green on main `0cd3464`; branch protection ON per Omar 2026-10-28; P6-04 extension of this workflow still open) |
| **DEP-02** | High | P5-01, P5-02 | 5 | deployment | T | Planned |
| **DEP-03** | High | P6-05 | 6 | deployment | T | Planned |
| **DEP-04** | Med | P3-01 | 3 | database | = DB-01 part | Planned |
| **DEP-05** | Med | P1-01 → P6-03 | 1,6 | auth | = SEL-01 + infra | Planned |
| **DEP-06** | Med | P6-06 | 6 | deployment | = FILE-06 (D-19) | Planned |
| **DEP-07** | Med | P6-01, P6-03 | 6 | deployment | T | Planned |
| **DEP-08** | Low | P6-02, P8-05 | 6,8 | deployment | T | Planned |
| **TST-01** | High | P2-02/03/04/05 | 2 | testing | T | **In Progress** (payload 1 = P2-02 **Verified**: PR #34 merged to `main` 2026-10-30 — merge commit `16868a8`; 5 suites / 42 tests, CI green, full suite 235/235; payload 2 = P2-03 delivered: PR #21, `0326b8b`, CI green, 72/72, Jira EB-37 = Done, 2026-10-29; payload 3 = error-contract IT set **delivered by EB-12** 2026-10-29: probes E1–E6, 80/80; payload 4 = P2-05 **implemented on branch `feat/p2-05-ws-integration-tests`** 2026-11-01: real-socket suites `WSAuthMatrixIntegrationTest` (9), `RoomBroadcastIntegrationTest` (CHAT-06 baseline), `PrivateConversationDeliveryIntegrationTest`; discovered + repaired the WS violation contract (drop + `/queue/errors`, connection-level CONNECT rejection), `JwtChannelInterceptor` updated; full suite 379 green — pending review/PR; P2-04/06 remain) |
| **TST-02** | Med | P2-01 | 2 | testing | T | **Verified** (merged to `main` 2026-10-28, commit `2a74075`; 14/14 green with zero env — `mvnw test` self-contained; Jira EB-35 = Done; decision D-27) |
| **TST-03** | Med | P2-07 | 2 | testing | T | **Verified** (CI live: green run `0cd3464`, red-run evidence documented (exit 126/exec bit); branch protection ON 2026-10-28; no-skip enforced) |
| **TST-04** | Low | P2-08, P8-07 | 2,8 | testing | T | Planned |
| **DOC-01** | Med | P5-05 | 5 | deployment | T | Planned |
| **DOC-02** | Low | P5-05 | 5 | deployment | T | Planned |
| **DOC-03** | Low | — | — | docs | RES (mds reorganization) | Resolved |
| **TD-01** | Low | — | — | tech-debt | DEF (risk>value now) | Deferred |
| **TD-02** | Low | P1-26 | 1 | tech-debt | T | Planned |
| **TD-03** | Low | P1-26 (+P1-12 dead code) | 1 | tech-debt | T | Planned |
| **TD-04** | Low | P1-26 | 1 | tech-debt | = INT-01 | Planned |
| **TD-05** | Low | P1-26 (+P5-02) | 1,5 | tech-debt | T | Planned |
| **TD-06** | Low | P5-05 | 5 | tech-debt | D→T (D-21) | Planned |
| **TD-07** | Med | P1-10 | 1 | ws | = G12 | **Implemented — EB-17 (2026-11-01):** createRoom ownership guard shipped (`SecurityService#isMentorOwnMentorship` before creation) — foreign/non-existent mentorship id → 403, owner → 200; see G12 rows — merged via [PR #59](https://github.com/MooRaa86/EduNest/pull/59) (merge `8b5f2f9`) — **Verified**, Jira EB-17 = Done |
| **TD-08** | Low | P1-12 | 1 | ws | = G13 (D-14) | **Implemented — EB-19** (see WS-01): the three inconsistent membership semantics collapsed into the two D-14 rules centralized in `ChatAuthorization` |
| **TD-09** | Low | P1-21 | 1 | file-storage | = G14 | **In Progress** — actual folder names in the public allowlist merged in [PR #23](https://github.com/MooRaa86/EduNest/pull/23); EB-28 acceptance remains open pending the reviews and coordination described for FILE-01. |

**Roll-up wrappers (not separate work):** `ARCH-001..007` resolve to their canonical rows (SEL-02, WS-01, SEL-01, DEP-02+broker, TD-01/02, DOC-01, TD-06 — see [`mds/ISSUES.md`](../mds/ISSUES.md) §18 cross-cutting rollups). `G1..G15` are the authorization-gap register; each row's canonical mapping lives in [`mds/ISSUES.md`](../mds/ISSUES.md) §1 — the plan implements canonical IDs (G-matrix rows are the same work: e.g., G4/G5→P1-14, G6→P1-14, G7→P1-16, G8→P1-15, G9→P1-20, G10→P1-11, G11→P1-19, G12→P1-10, G13→P1-12, G14→P1-21, G15→P1-19).

**Not tracked (by design):** `mds/ISSUES.md` §19 disconfirmed suspicions — no tasks; recorded as regression-source material for P2-03.

**Counts:** 97 unique issue rows mapped: 2 deferred (SEL-13, TD-01), 1 resolved (DOC-03), 7 verification-first (SEL-15, MSH-12, MSH-05, FILE-03, FILE-04, QZ-06, CHAT-08), 34 with decision linkage (default path defined for all), the remainder straight implementation tasks. No issue is unmapped.
