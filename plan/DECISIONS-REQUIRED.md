# Decisions Required

26 open decisions. Each states the question, options, impact, blocked tasks, recommended default (only where the audit evidence supports one), and the latest reasonable decision point expressed **by task/phase dependency, never by calendar**.

> Security-adjacent defaults: work proceeds on the documented default without waiting, per [`EXECUTION-STRATEGY.md` §1.5](EXECUTION-STRATEGY.md#1-principles) — an override re-opens the mapped task but not the phase.

## Index

| ID | Decision | Owner role | Decide-by (latest) | Blocked work |
|---|---|---|---|---|
| D-01 | JWT session invalidation model | PO + Eng Lead | P1-27 unblocks | P1-27, P2-03 (test shape) |
| D-02 | DRAFT mentorship visibility | PO | Before P1-15 | P1-15 |
| D-03 | Confirm confinement of unowned content | PO | Before P1-14 (formality) | P1-14 |
| D-04 | Public-surface privacy (emails on reviews/leaderboards) | PO + FE liaison | Before P1-16 | P1-16 |
| D-05 | Disposition of open push/mail endpoints | PO | ✅ Decided 2026-10-25 (delete) | P1-11 (re-scoped: delete) |
| D-06 | Fate of generic `GET /files?filePath=` | Eng Lead + FE liaison | Before P1-20 | P1-20 |
| D-07 | Upload exposure policy & FE link changes | PO + FE liaison | ✅ Decided 2026-10-25 (Supabase Storage) | P1-21 (re-scoped), P7-02 |
| D-08 | Late-submission & quiz close policy | PO | Before P4-04 | P4-04 |
| D-09 | Points reconciliation & completion freeze rules | PO | Before P4-02 | P4-02 |
| D-09a | Legacy Submission Grading Policy (Double-Credit Prevention) | PO + Eng Lead | ✅ Decided 2026-11-08 (Integrity First) | EB-51 (P4-02) |
| D-10 | `passPoints`/unlock: implement or retire | PO | ✅ Decided 2026-11-14 (defer this sprint) | P4-15 (deferred) |
| D-11 | Certificate PDF feature: implement or claim-update | PO | Before P5-05 (deferral allowed) | P5-05 content, optional feature |
| D-12 | Admin notification broadcast model | PO + Eng Lead | Before P4-01 slice | P4-01 |
| D-13 | Chat read receipts scope | PO (deferral allowed) | Before P4-07 | P4-07 (conditional) |
| D-14 | Chat membership semantics | PO + Eng Lead | ✅ Decided 2026-10-25 (fix approved) | P1-12 (unblocked) |
| D-15 | Live-session rules: attendance constants + auto-close | PO | Before P4-08 | P4-08 |
| D-15b | Live Session Sweep Concurrency & Transaction Boundary Architecture | Eng Lead + Infra | ✅ Decided 2026-11-08 (Option B Worker) | EB-51 (P4-02), P4-08 |
| D-16 | Live-video platform (Jitsi public vs self-host JWT vs Agora) | PO + Infra | ✅ Decided 2026-10-25 (Zoom) | P4-17 (re-scoped: Zoom integration) |
| D-17 | Fresh-deployment default commission rate | PO | Before P3-05 | P3-05 |
| D-18 | Soft-deleted users' visibility on public reads | PO | Before P3-04 | P3-04 |
| D-19 | Git history cleanup approval for committed uploads | Eng Lead + PO | Before P6-06 execution | P6-06 |
| D-20 | API envelope/path standardization scope | Eng Lead + FE liaison | Before P4-16 (deferral expected) | P4-16 (conditional) |
| D-21 | `features/roadmap/**` fate | PO | Before P5-05 | doc sub-item |
| D-22 | Hosting & environments (staging/prod target, DNS/TLS) | PO + Infra + Eng Lead | ✅ Decided 2026-10-25 (container + Supabase) | P6-02+ §all infra; P7-05+ (unblocked) |
| D-23 | CORS allowed-origins values | PO + FE liaison | Before P1-23 | P1-23 |
| D-24 | OSIV disable timing | Eng Lead | Before P3-06 (deferral OK) | P3-06, P4-11 |
| D-25 | Upload storage: volume vs object storage | PO + Infra | ✅ Decided 2026-10-25 (Supabase Storage S3) | P6-07 → superseded by [ADD-03] |
| D-26 | Error-body disclosure level | Eng Lead + FE liaison | Before P1-05 | P1-05 |
| D-07a | *(sub-decision inside D-07)* whether FE fetches public assets by URL or via authenticated endpoints | FE liaison | With D-07 | FE contract change |

### D-01 — JWT session invalidation model
- **Question:** What replaces the 7-day, non-revocable token (SEL-06)?
- **Options:** (a) short-lived access (≤30 min) + refresh endpoint at `/login-api/refresh`; (b) keep 7-day tokens + server-side `lastInvalidatedAt` checked by the validator filter; (c) status quo (rejected by audit).
- **Impact:** (a) best posture + biggest FE change (refresh flow); (b) smallest change, revokes on next request, slight DB cost per request; (c) cannot logout.
- **Blocked:** P1-27, P2-03.
- **Default:** (a) with a transitional period allowing 7-day tokens to be marked short (evidence: validator already re-checks DB per request — (b) is the low-risk fallback if FE integration stalls).
- **Owner:** PO + Eng Lead. **Decide-by:** before P1-27 (Phase 1).
- **Outcome (decided 2026-10-31, user-approved):** **(a) narrowed — stateless JWT stands for Sprint 1.** No refresh tokens and no server-side revocation model this sprint. Logout invalidation is purely client-side token erasure (the erasure contract already enforced by EB-11: credentials wiped post-auth, token served in `Authorization` header). **Refresh tokens + real session/revocation model move to the Sprint 2 backlog** — `P1-27` is formally **carried with this decision record** (per its acceptance criterion: "implemented per D-01 or formally carried with decision record"), so `EB-34` is unblocked to scope/codify, not to build refresh. Fallback if the audit or FE forces earlier revocation: option (b) (`lastInvalidatedAt` validator check) may be adopted only via a new decision line.
- **Override (2026-11-02, user-approved — team decision after review):** **option (a) is now IN — the "(a) narrowed" outcome above is superseded.** Refresh tokens with a proper server-side session-revocation model are adopted for delivery; rotate-at-use design review happens in the blueprint step. Executing ticket: **EB-91** ("[P2] Secure Refresh Token Rotation & Session Revocation (D-01 / SEL-06)"), **pulled into Sprint 2** (label `sprint-2`). Consequences: `P1-27`/`SEL-06` disposal becomes "fix (per this override)" — refresh endpoint lands at `/login-api/refresh`, short-lived access tokens, server-side revocation; transitional client-side erasure contract (EB-11) stays valid during rollout; FE contract impact must be re-opened with the FE liaison channel before merge of the implementing PR, per the original option-(a) impact row.

### D-02 — DRAFT mentorship visibility
- **Question:** Is DRAFT exposure acceptable "coming soon" behavior (MSH-01)?
- **Options:** (a) hard-restrict DRAFT to owner+ADMIN everywhere; (b) restricted detail + public teaser via overview; (c) keep public.
- **Impact:** (a) closes leak + breaks "browse drafts" UX if intended; (b) needs designed teaser surface; (c) unreleased pricing/content leaks (current behavior).
- **Default:** (a) — audit found no intentional surface for DRAFT browsing.
- **Decide-by:** before P1-15.

### D-03 — Confinement of unowned content (G4/G5/G6)
- **Question:** Confirm lectures/task-lists/details are for enrolled students + owning mentors only.
- **Options:** (a) enforce enrollment-based access (recommended); (b) allow logged-in preview (broad leak) — not recommended.
- **Default:** (a). Decide-by: before P1-14; formal confirmation only.
- **Outcome (decided 2026-10-31, user-approved):** **(a) confirmed.** Course content (lectures, task lists, details) is readable exclusively by: **(1) enrolled students of that course, (2) the owning mentor, (3) ADMIN.** Everyone else is denied. Confirmation covers preview surfaces too (no logged-in browsing of content). Unblocks `P1-14` (Jira **EB-21**, High — first security mover).

### D-04 — Public-surface privacy
- **Question:** Which fields stay public on reviews/leaderboards/public profiles (MSH-03/09, USR-02)? Emails exposed today.
- **Options:** (a) display names only, no emails/photos beyond needed; (b) pseudonyms; (c) status quo.
- **Impact:** (a) smallest code change + real privacy win; FE must render names.
- **Default:** (a). Decide-by: before P1-16.
- **Outcome (decided 2026-10-31, user-approved):** **(a) display names only** — public surfaces (reviews, leaderboard, public profiles) expose the user's **display name and points only** — no emails, no phone, no other personal profile data. FE renders names instead of emails. Unblocks `P1-16` (Jira **EB-23**, Leaderboard Privacy).
- **Amendment (decided 2026-10-31, user-approved, PO call):** the **leaderboard is NOT a public surface anymore** — PR #39 gates it to enrolled cohort members + owning mentor + ADMIN (anonymous 401, outsiders 403). On surfaces gated to the enrolled cohort, ruler **B** applies: rows keep `studentFullName`, `totalPoints`, `rank`, `studentProfileImageUrl`, and `studentEmail` (FE profile click-through). `studentLevel` and badge fields stay removed (no personal data beyond needed). **Public surfaces (reviews / public profiles) remain (a): display name + rating only, no emails, no photos.** FE navigates profiles by email on cohort-gated surfaces only.
- **Proposed follow-up amendment (2026-10-31, on PR #39 — PENDING Engineering Lead confirmation, not yet approved):** public review surfaces additionally expose the reviewer's **profile image** (`userProfileImageUrl`) together with the display name, to restore FE card visuals. Reviewer **emails stay private** on all public review payloads (both prompts/staged "no emails" rule unchanged). This note intentionally does NOT rewrite the Ruler B amendment or the original outcome above; the leaderboard/gated-surface rules are unaffected. Until Omar approves, this restore is treated as unapproved in-flight work.
- **Proposed compatibility exception (PENDING Engineering Lead / Product Owner approval):** Temporarily restore reviewer email on public reviews as `studentEmail` for mentorship reviews and `userEmail` for mentor-profile reviews, solely for backwards compatibility until usage in the separate frontend is verified. This proposed exception supersedes the preceding proposal's email exclusion only if approved; public email exposure is not yet privacy-approved. The original decision and Ruler B leaderboard amendment remain unchanged.
- **Engineering Lead approval (Omar):** The temporary public-review compatibility exception is approved, superseding the preceding proposals' pending status and email exclusion. Mentor-profile reviews retain `userEmail` and `userProfileImageUrl`; mentorship reviews retain `studentEmail` and `userProfileImageUrl`, for frontend backwards compatibility. Frontend usage verification remains pending; any future removal of reviewer emails requires coordination with the separate frontend project. The enrollment-restricted leaderboard and its Ruler B contract remain unchanged. Approval was communicated in the task instructions; no approval timestamp is recorded.

### D-05 — Open push/mail endpoints
- **Question:** `POST /api/v1/notifications/send` (any→any), `POST /admin/.../send-notification`, and public `POST /send` platform mailbox (SEL-10/NOTIF-01, MAIL-01) — admin-gate, restrict, or delete?
- **Options:** (a) admin-gate (keep tooling); (b) delete outright; (c) keep public (unacceptable).
- **Impact:** (a) preserves ops tooling + closes phishing vector; (b) cleanest but loses messaging utility.
- **Default:** (a). Decide-by: before P1-11.
- **Outcome (decided 2026-10-25, user-approved):** **(b)-shaped — DELETE.** The endpoints are test leftovers. Execution: inventory all test-only APIs, confirm with FE/ops that none are load-bearing, then delete with removal evidence. `P1-11` re-scoped from "gate" to "delete" (Jira EB-18); a follow-up addendum task **[ADD-04]** (Jira EB-84) covers the broader test-endpoint inventory. No admin-gate.

### D-06 — Generic file endpoint fate
- **Question:** Keep `GET /files?filePath=` guarded via `FileAccessService` allowlist, or retire it entirely (does FE depend on it)?
- **Default:** guard-first (retire when FE confirms non-use). Decide-by: before P1-20.
- **Outcome (decided 2026-10-31, user-approved):** **Guard-first confirmed as the standing rule.** The endpoint stays under the `FileAccessService` allowlist. The already-merged temporary protection counts as **partial progress, not task closure** — `P1-20` (Jira **EB-27**) remains open until FE confirms the endpoint is unused, at which point it is retired outright.

### D-07 — Upload exposure policy (P0-critical for FE)
- **Question:** Which `uploads/**` folders remain publicly URL-fetchable and which become authorization-checked (submissions must be private)?
- **Options:** (a) public: covers/profiles/chatroom images; private: submissions — served via ownership-checked `/files/**`; (b) everything authenticated (FE rework of asset loading); (c) status quo (public submissions — unacceptable).
- **Impact:** (a) moderate FE change for submission links only; (b) large FE change.
- **Default:** (a). Decide-by: before P1-21; FE confirmation documented on the task.
- **Outcome (decided 2026-10-25, user-approved):** uploads move to **Supabase Storage (S3)** with bucket security policies + server-side ownership enforcement. The security intent of (a) is preserved: public assets (covers/avatars/chatroom images) via bucket-URL policy; submissions private (owner + mentor) via signed URLs or an authenticated proxy. **[ADD-03]** (Jira EB-83) implements the storage integration; `P1-21` (EB-28) re-scoped to submission-access policy enforcement + FE contract note. Local-disk serving code is retired.

### D-08 — Late submissions & quiz window
- **Question:** Define late policy (LEARN-05): accept-with-flag? reject? penalty?; when do quizzes hard-close (LEARN-10)?
- **Default (evidence-backed):** reject late *first* submissions is harsh; accept-with-penalty config `late.penalty.perMinute` default adopt existing −1pt/min rule; quizzes auto-close via scheduler at `durationMinutes` + grace. Decide-by: before P4-04.
- **Outcome (decided 2026-11-11, Project Owner-approved):** final policy (differs from the earlier default):
  - **Tasks/projects (LEARN-05):** late **first** submissions are accepted only within a configurable grace
    window (`app.submission.late-grace-minutes`, default **10 min**), flagged `isLate=true` with **no automatic
    grade deduction** — mentor grading stays authoritative. Beyond grace, first submissions are **rejected**
    (front-end-visible 4xx) before any mutation. Late **re**-submissions remain rejected (pre-EB-53 rule kept).
  - **Quizzes:** the −1 pt/min soft penalty is **kept but clamped at 0** and applies **only inside the grace
    window**; beyond grace the submission is **rejected before any mutation** (no score, no answers, no
    TotalPoints delta; `isLate` stays null). Late (within-grace) submissions persist/expose additive
    `quiz_submission.is_late` (`isLate` on student response + mentor listing rows). Note: mentor-graded
    submissions (task/project) use the flag-without-deduction mechanism, while auto-scored quizzes use the
    per-minute deduction mechanism — same grace window, different penalty mechanisms.
  - **Quiz close (LEARN-10):** auto-close **requires an explicit `quiz.closeAt`** field (nullable V12 column,
    additive) and is **never derived** from max attempt endDates. Legacy PUBLISHED quizzes with `closeAt == null`
    are never auto-closed and never backfilled. Publishing (create-as-PUBLISHED, updateQuiz DRAFT→PUBLISHED,
    changeStatus → PUBLISHED) requires a valid future `closeAt`. New attempts are blocked once `closeAt` passes
    (before any attempt row is created); in-progress attempts keep their own `endDate` + grace. The
    `@Scheduled` sweep (idempotent, canonical week→quiz lock order, D-15b Option B non-TX dispatcher +
    `@Transactional` worker) transitions PUBLISHED→CLOSED only when `closeAt` passed **and** no attempt is
    eligible to submit (`endDate + grace > now`); mentor manual close is unaffected.
  - **Implementation:** branch `feat/p4-04-quiz-late-policy` (EB-53), migration
    `V12__quiz_close_at_and_submission_is_late.sql`, probes `QuizLatePolicyPostgresTest` (16 cases).

### D-09 — Points reconciliation & freeze rules
- **Question:** Can mentors edit task/project/quiz points after grading (LEARN-06)? Badge point edits after mentorship COMPLETED (CERT-04)? Bulk-update clamp policy (GAME-03)?
- **Options per axis:** allow+recalculate vs lock-after-first-grade; freeze badge edits on COMPLETED; clamp ≥0 always.
- **Default:** lock content points after any grade exists (regrade flows recompute explicitly); freeze badge edits on COMPLETED; clamp everywhere. Decide-by: before P4-02.

- **Outcome (decided 2026-11-07, user-approved):** the documented default is adopted as-is — content point
  values **lock after a qualifying grade exists** (task/project point edits and graded-content deletion are
  rejected); **explicitly supported regrading is preserved** and recomputes the credited delta through the
  service-level ledger guard; **badge point changes freeze once the mentorship reaches COMPLETED** (CERT-04);
  **TotalPoints clamps at zero everywhere** (negative deltas floor at 0, never negative). No additional policy
  axes were introduced. EB-51 (P4-02) local implementation verified: managed version-aware ledger writes with optimistic locking (V8), coordinated hierarchical gates (L0-L4), provisional task/project any-submission freeze (protecting ambiguous legacy grading records), week cascade-delete protection, scored-quiz deletion guard, and post-completion badge freezes (CERT-04).
- **Legacy grading blocker:** `pointsApplied != null`, including zero, records a contribution under the
  current flow. A null value on an existing task/project submission does not prove it was never graded:
  legacy grading may not have populated the field, and resubmission clears other grade evidence. Do not
  infer an old contribution from the aggregate ledger. A trustworthy historical source or an approved
  workflow decision is required before treating all such submissions as safe first grades. A proposed
  read-only, mentorship-scoped audit appears in `mds/database/transactions-consistency.md`; it has not
  been run against production.

### D-09a — Legacy Submission Grading Policy (Double-Credit Prevention)
- **Question:** How should `TaskSubmissionServiceImpl.grade` and `ProjectSubmissionServiceImpl.gradeProject` handle legacy submissions where `pointsApplied == null`?
- **Background & Schema Limitation:**
  In `task_submission` and `project_submission`, `points_applied` is `NULL` for:
  1. Fresh submissions that have never been graded.
  2. Legacy submissions that were graded prior to Sprint 2 where points were added to `TotalPoints`, but `points_applied` was unpopulated.
  Furthermore, the Sprint 1 resubmission path actively set `final_score = null`, `raw_score = null`, and `graded_at = null` on resubmit.
  The database maintains no immutable transaction ledger table of individual point credits.
  Therefore, it is impossible from database columns alone (`submission_status`, `rawScore`, `finalScore`, or `TotalPoints`) to distinguish a never-graded submission from a previously graded-and-resubmitted submission.
- **Double-Credit Vulnerability Path:**
  If an ambiguous legacy submission was graded prior to Sprint 2 and points were credited to `TotalPoints`, regrading it today evaluates `pointsApplied == null ? 0 : pointsApplied`. The delta $\Delta = \text{newScore} - 0 = \text{newScore}$ credits the full score to `TotalPoints` a second time, silently inflating student points and corrupting leaderboards.
- **Classification Categories:**
  - `KNOWN_CREDIT`: Recorded prior contribution is trustworthy (`points_applied IS NOT NULL`). Safe to regrade ($\Delta = \text{newScore} - \text{points_applied}$).
  - `KNOWN_FIRST_GRADE`: Reliable provenance proves no earlier credit exists. *(Currently, no reliable automatic classifier exists in the database schema; Git commit dates are not deployment evidence, and production rollout timestamps are unverified).*
  - `AMBIGUOUS_LEGACY_HISTORY`: Earlier credit cannot be established or excluded (`points_applied IS NULL`).
- **Classification Feasibility & Blocker:**
  Because the database schema lacks an immutable historical ledger and resubmissions erased grading evidence, **a trustworthy automatic classifier for `KNOWN_FIRST_GRADE` cannot be implemented reliably with current data.** Marking un-audited submissions as safe first-time grades based on heuristics or commit dates is unsafe. This classification is therefore formally marked **BLOCKED** pending a product decision.
- **Audit Override Security (Client Headers Strictly Rejected):**
  - **Direct client headers (such as `X-Audit-Baseline-Points`) are REJECTED.** Arbitrary callers must never be permitted to supply unverified historical baselines that mutate points.
  - A future admin-only remediation workflow is designed (not implemented yet) requiring:
    1. Explicit admin authorization (`Roles.ADMIN` only).
    2. Independently verified external evidence (e.g. historical log exports or mentor gradebooks).
    3. Immutable audit trail logging (recording auditor identity, timestamp, before/after balances, rationale, and proof reference).
    4. Explicit submission and student identity verification.
    5. Mentorship scope boundary validation.
    6. Idempotency (repeated baseline execution produces identical state without re-incrementing).
    7. Transactional ledger consistency (updating `points_applied` and adjusting `TotalPoints` atomically).
    8. Dual-approval / review controls for material points corrections.
- **Business-Policy Tradeoff for Product Owner Decision:**
  - *Option 1 (Tolerate Double-Credit Risk):* Allow mentors to grade all `points_applied IS NULL` submissions as first-time grades. Tradeoff: eliminates mentor friction on legacy deliverables, but accepts unverified points inflation on students who resubmitted after Sprint 1 grading.
  - *Option 2 (Strict Integrity Gate — Recommended):* Reject grading on any submission where `points_applied IS NULL` with HTTP 422 `ERR_LEGACY_SUBMISSION_REQUIRES_AUDIT`. Tradeoff: completely prevents points ledger corruption and double-credit, but requires admin audit/clearance for legacy submissions prior to grading.
- **Decision Status:** ✅ **DECIDED 2026-11-08 (Integrity First Policy Approved by Product Owner & Engineering Lead).**
  - **Adopted Policy:** Strict Integrity First. Historical points must never be credited a second time when prior contribution cannot be reliably established. Non-null `pointsApplied` is authoritative only when recorded. Unverified creation-date cutoffs are rejected. All client-controlled baseline headers (`X-Audit-Baseline-Points`) are purged and rejected.
  - **Implementation Contract:**
    - Submissions that were previously graded (`status == GRADED || gradedAt != null || finalScore != null`) with `pointsApplied == null` fail closed with `LegacySubmissionAuditRequiredException` mapped to HTTP 422 `ERR_BUSINESS_RULE`.
    - Fresh first-time submissions (`pointsApplied == null && status == SUBMITTED && gradedAt == null`) proceed normally to grade for the first time without administrative blocking.
    - Verified by `LegacySubmissionGradingSafetyTest` (6/6 passing) and `GradingPointsLeaderboardFlowIntegrationTest` (3/3 passing). Zero test fixtures broken.

### D-10 — passPoints/unlock
- **Question:** Implement checkpoint unlock semantics (feature) or remove `passPoints` + fix claims?
- **Default:** defer as feature; remove the dead field's implication from claims (P6-05 docs) if unapproved. Decide-by: before P4-15.
- **Outcome (2026-11-14, PO-approved):** Defer checkpoint unlocking for the current sprint. The feature is **not implemented**: `passPoints` is stored but does not enforce unlocking. No prerequisite enforcement is authorized, and existing student content access remains unchanged.
- **Compatibility exception:** Preserve the existing `passPoints` API field and database column temporarily for backward compatibility. This PO-approved retention supersedes the original P4-15 deferral-cleanup recommendation to remove the field. Future implementation or field removal requires a new PO decision and a reviewed implementation or API/schema retirement plan.

### D-11 — Certificates PDF
- **Question:** Implement iText-rendered PDF certificates (feature) or correct README/books claims?
- **Impact:** implementation is XL (layout, file storage, email attach, FE download) vs doc-only.
- **Default:** correct claims now; revisit feature post-launch. Decide-by: before P5-05.

### D-12 — Admin notification broadcast
- **Question:** Admin pushes to newest admin only (NOTIF-04) — broadcast to all admins or a fixed role assignment?
- **Default:** broadcast to all `Roles.ADMIN` accounts. Decide-by: before P4-01.

### D-13 — Chat receipts
- **Question:** Build unread/read-stat model for chat (CHAT-07) in this cycle?
- **Default:** defer to post-launch unless FE sprint has room (clearly medium feature). Decide-by: before P4-07.
- **Outcome (decided 2026-11-12, PO-approved Omar):** **DEFER** — *"I don't need to make Chat receipts/unread feature now. I need to keep the chat messages like it now, because it's a learning platform, not mandatory — this feature we may in the future add it, but now no."* Chat surface stands as-is (delivery + persistence unchanged, no read-stat model); matches the documented default (defer to post-launch). Jira EB-56/CHAT-07 dispositioned accordingly (closed-with-deferral note; no code changes). Revisit post-launch if wanted — the P4-07 spec remains the reference.

### D-14 — Chat membership semantics *(decide early — gates P0 task P1-12)*
- **Question:** Who may subscribe/post in `chat-room`: enrollment-proxy (any enrolled student) or explicit `chat_room_members` only?
- **Impact:** defines the interceptor rule + repository membership query; affects every cohort user.
- **Default:** membership = true members ∪ mentor-owner ∪ enrollment-proxy READ-only? — recommendation: **write: member or mentor-owner; read: member/mentor/enrolled (enrollment-proxy)**. Decide-by: before P1-12 (Phase 0 round).
- **Outcome (decided 2026-10-25, user-approved):** **FIX approved** (status quo rejected). WebSocket security is committed: room authorization + dead-code removal in `P1-12` (Jira EB-19), with access-control hardening hooks added to the WS path. Access rules follow the plan default above unless the team revises during implementation.

### D-15 — Live-session rules
- **Question:** Approve attendance math constants (verify ≥75%→pass, snapshot 5-min grid, +5 bonus, absence→0) and auto-close scheduler (LS-02/03)?
- **Default:** constants into config properties (values unchanged), scheduler closes ENDED-state after actualEnd + grace. Decide-by: before P4-08.
- **Outcome (decided 2026-11-02 by Omar Medhat, user-approved):**
  - **Auto-close sweep:** ACTIVATED via `LiveSessionSweepService` with `@Scheduled` sweep interval set to **every 5 minutes** (`app.livesession.auto-close.sweep-rate-ms: 300000`, adjusted from 60s per Omar's directive to optimize DB load). Enforces a 10-minute grace window (`grace-minutes: 10`) after the 40-minute duration cap (`max-duration-minutes: 40`, D-29 Basic plan). Automatically transitions expired LIVE sessions to `ENDED` (signaling Zoom to end the meeting) and stale un-started SCHEDULED sessions to `CANCELLED` (deleting Zoom meeting).
  - **Attendance & Gamification:** Enforcement is **DORMANT** by default (`app.livesession.attendance.enforced=false`). Strategy Pattern implemented (`SnapshotGridAttendancePolicy` and `DefaultLivePointsAwarder` compiled and ready for activation; `NoEnforcementAttendancePolicy` and `DormantPointsAwarder` active by default). When dormant, session end records no absence verdicts, marks no absences, and executes zero gamification/leaderboard writes. Flipping `app.livesession.attendance.enforced=true` activates the full snapshot grid (75% threshold, 5-min grid, +5 bonus) without code changes.
  - **Transactional Boundaries:** `@Transactional` added to `startLiveSession` and `recordSnapshot` (LS-05); snapshot persistence batched via `saveAll` (LS-04).

### D-15b — Live Session Sweep Concurrency & Transaction Boundary Architecture
- **Question:** How should `LiveSessionSweepService` orchestrate transactions and database locks when sweeping expired live sessions across multiple mentorships?
- **Background:**
  The baseline `LiveSessionSweepService.autoCloseExpiredSessions` runs on a background `@Scheduled` cron (every 5 minutes per D-15). In the original implementation, the entire sweep executed in a single top-level `@Transactional` boundary, iterating over all expired sessions across all mentorships. In EB-51, in-memory sorting was added by `(mentorshipId, weekId, sessionId)`.
  **Critical Architectural Realities:**
  1. *In-memory Java sorting is NOT proof that database locks were acquired in order.* Rows are locked in the database only when queries with pessimistic write locks (`FOR UPDATE`) execute against the database.
  2. *Exactly-once points awarding cannot be claimed without database-level idempotency evidence.* `session_attendance_result` lacks a database-level unique constraint on `(session_id, student_id)`, meaning concurrent sweep or manual end execution can insert duplicate results and award points twice.
  3. *`REQUIRES_NEW` is NOT approved and is NOT strictly necessary.* A separate worker bean with ordinary `REQUIRED` propagation invoked from a non-transactional dispatcher provides identical per-session transaction isolation without the connection-suspension overhead of `REQUIRES_NEW`.
- **11-Dimension Comparative Evaluation:**
  | Dimension | Option A: Grouped Transaction with Ordered DB Gates | Option B: Per-Session Worker (`REQUIRED` + Non-TX Dispatcher) |
  | :--- | :--- | :--- |
  | **1. Lock Scope & Blast Radius** | Global: locks held across multiple mentorships, weeks, and sessions in one transaction. Failure in one session rolls back all swept sessions. | Isolated: locks restricted to a single mentorship, week, and session per transaction. Failure in one session affects only that session. |
  | **2. Lock Duration (Connection Hold Time)** | Long: database connection and row locks held across the entire batch sweep, including all Zoom API checks, JPA queries, and attendance writes. | Short: connection and locks held only for the duration of a single session closure (milliseconds). |
  | **3. Failure Isolation & Rollback** | All-or-nothing: if the 10th session encounters a database constraint or transient error, the preceding 9 closed sessions roll back. | Complete isolation: each session commits independently. Successfully closed sessions stay closed. |
  | **4. Deadlock Surface with User TXs** | Moderate-to-High: holding locks across multiple mentorships increases lock contention with concurrent user transactions (e.g. mentor ending session, student submitting quiz). | Minimal: sweep worker acquires L0 → L1 → L2 locks for exactly one session at a time in the exact same order as user endpoints. |
  | **5. Connection Pool Starvation Risk** | Low-to-Moderate: uses 1 connection for the entire sweep, but holds it for a sustained duration during cron execution. | Minimal: uses 1 connection briefly per session; connections return to pool immediately between iterations. |
  | **6. Dialect Portability (Postgres / H2)** | Dialect-agnostic (standard JPA queries + `@Lock(PESSIMISTIC_WRITE)`). | Dialect-agnostic (standard Spring `@Transactional` + `@Lock(PESSIMISTIC_WRITE)`). Tested cleanly on PG 17 and H2. |
  | **7. Code Complexity & Maintenance** | Very low: single service class, single method with loops. | Low: requires introducing a `LiveSessionSweepWorker` bean to host isolated `@Transactional` processing. |
  | **8. Scalability (Many Active Sessions)** | Poor: transaction execution time scales linearly with total number of expired sessions across all mentorships. | High: constant transaction size ($O(1)$ per unit of work); easily parallelizable in future sprints if needed. |
  | **9. Out-of-Band External Side Effects** | High risk: Zoom API calls or notifications inside the giant transaction can stall the DB transaction or leave external systems out of sync on DB rollback. | Clean boundary: external side effects can be executed per-session with explicit after-commit hooks or before-commit guards. |
  | **10. Observability & Metrics Granularity** | Low: sweep reports 1 aggregate result for the entire run. Individual session failure reasons are swallowed or cause whole-batch abort. | High: structured per-session logging (`sessionId`, `mentorshipId`, elapsed time, status transition) and per-session error metrics. |
  | **11. Implementation & Regression Risk** | Minimal immediate code changes, but carries latent production concurrency risks. | Low implementation risk (clean Spring component separation), zero regression to existing attendance policies. |
- **Recommendation:** **Option B with ordinary `REQUIRED` propagation and a non-transactional dispatcher** is the recommended architecture for production resilience.
- **Lock Protocol for Option B:**
  1. Outer sweep orchestrator (non-transactional) queries list of candidate expired session IDs ordered deterministically by `(mentorshipId, weekId, sessionId)`.
  2. For each session ID, invoke `LiveSessionSweepWorker.processSessionClose(sessionId)` annotated with `@Transactional(propagation = Propagation.REQUIRED)`. Because the dispatcher is non-transactional, Spring begins a fresh transaction for each invocation.
  3. Inside worker, acquire locks in canonical order: L0 `mentorShipRepository.findForUpdateById(mentorshipId)` $\to$ L1 `weekRepository.findForUpdateById(weekId)` $\to$ L2 `sessionRepository.findForUpdateById(sessionId)`.
  4. Verify session state is still eligible for auto-close (double-check guard under lock).
  5. Close session, evaluate attendance policy, commit transaction.
- **Decision Status:** ✅ **DECIDED 2026-11-08 (Option B Approved by Product Owner & Engineering Lead).**
  - **Adopted Architecture:** Dedicated Spring component `LiveSessionSweepWorker` executing with `@Transactional(propagation = Propagation.REQUIRED)` called by non-transactional dispatcher `LiveSessionSweepService`.
  - **Locking & Concurrency Contract:**
    1. Outer orchestrator queries candidate sessions sorted deterministically by `(mentorshipId, weekId, sessionId)`.
    2. Dispatcher iterates over candidate sessions, delegating each to `sweepWorker` in an isolated try-catch block. A failure or deadlock on one session rolls back only that session, allowing all other sessions to commit independently (Dimension 3: Failure Isolation).
    3. `LiveSessionSweepWorker` acquires row locks strictly in canonical hierarchy: L0 mentorship (`mentorShipRepository.findForUpdateById`) -> L1 week (`weekRepository.findForUpdateById`) -> L2 session (`liveSessionRepository.findForUpdateById`).
    4. Authoritative session status is rechecked under pessimistic write lock (`status == LIVE` / `SCHEDULED`). If status changed concurrently (e.g. mentor called `endSession`), the worker skips without double-processing.
    5. Database-level idempotency guaranteed by Flyway migration `V10__unique_session_attendance_result.sql`, creating unique index `uk_session_attendance_result_session_student` on `session_attendance_result(session_id, student_id)` and archiving duplicates in `eb51_archive_session_attendance_result`.
    6. Verified by 20/20 tests passing in `TotalPointsPostgresIntegrationTest` (including `liveSessionSweepAndManualEndSessionSerializeSafelyOnPostgres` on PostgreSQL 17), 8/8 in `LiveSessionHousekeepingIntegrationTest`, and 10/10 in `LiveSessionSweepArchitectureScenariosTest`.

### D-16 — Live-video platform
- **Question:** Keep public `meet.jit.si` rooms (update docs), self-host Jitsi with JWT-gated rooms, or evaluate Agora SDK (dependency present but **unused** — planning-phase discovery, see [RISKS §6](RISKS-AND-DEPENDENCIES.md#6-planning-assumptions-register))?
- **Impact:** self-host is infra XL; keep-public is doc-truth work.
- **Default:** keep public + truthful docs this cycle. Decide-by: before P4-17.
- **Outcome (decided 2026-10-25, user-approved):** **new option (d) — adopt Zoom** for live sessions/meetings. Meetings are created/owned through the Zoom API (recommended: Server-to-Server OAuth app); access control uses Zoom-native require-authentication/passcode instead of hand-distributed public `meet.jit.si` URLs — narrowing LS-01's open-room exposure at the source. `P4-17` re-scoped to **[P4-17] Zoom meeting integration** (Jira EB-66); addendum **[ADD-05]** (Jira EB-85) provisions the Zoom app/credentials/env. Attendance flows (LS-02..05) unchanged. The unused Agora dependency (A-8/R11) is confirmed removable in P1-26's dead-dep audit.

### D-17 — Default commission rate
- **Question:** What value should a fresh deployment seed (MSH-05)? (Current: unset → enrollments fail until admin sets.)
- **Default:** seed 10% via migration, overridable by admins. Decide-by: before P3-05.

### D-18 — Soft-deleted users on public reads
- **Question:** Filter deleted users from leaderboards/reviews/chat participants (DB-03)?
- **Default:** filter on public surfaces; admin surfaces keep full visibility. Decide-by: before P3-04.

### D-19 — Git history cleanup
- **Question:** Approve history purge of committed `uploads/**` user files (FILE-06)?
- **Impact:** force-push coordination for all contributors; permissions.
- **Default:** yes (privacy). Decide-by: before P6-06 execution.
- **Outcome (decided 2026-11-13, user-approved — EB-77):** **Waived for history purge; superseded by Supabase Storage migration ([ADD-03]/EB-83) and CI hygiene guard.**
  * *Context & Findings:* Forensic audit confirmed that historical files under `uploads/**` (18 unique blobs, ~4.5 MB across 30 paths committed between Feb–Jun 2026) were synthetic developer test fixtures and dummy templates with zero real user PII, credentials, or secrets.
  * *Storage Architecture:* Production file storage was migrated to Supabase Storage (S3 API) under [ADD-03]/EB-83 with dual public/private buckets and fail-closed runtime enforcement (`StorageFailClosedTest`), completely eliminating local storage fallback in production.
  * *Disproportionate Impact:* Rewriting Git history (`git-filter-repo`) would mutate 566 commits across all 142 branches back to repository inception (`a3ddabf`, 2026-02-07). This would invalidate contributor clones, break active PRs (including PR #129 / EB-115), orphan commit references in Jira and documentation, and create a critical risk of triggering Railway Auto Deploy on the `production` branch.
  * *Resolution:* The destructive history purge and force-push are waived. Repository hygiene is permanently enforced via a non-disruptive CI guard (`scripts/cicd/repo-hygiene-gate.sh` in `.github/workflows/tests.yml`) that rejects any tracked upload files in HEAD or introduced within PR commit ranges.

### D-20 — API envelope standardization
- **Question:** Unify `SimpleResponse` vs typed DTOs vs `PageResponse` (API-01) and normalize legacy path styles (API-03) now or later?
- **Default:** defer both (breaking-change cost vs zero user value mid-stabilization). Decide-by: before P4-16.

### D-21 — Roadmap module
- **Question:** `features/roadmap/**` empty skeleton — implement or delete (TD-06)?
- **Default:** delete the skeleton (dead code). Decide-by: before P5-05.

### D-22 — Hosting & environments *(earliest infra gate)*
- **Question:** Target platform for staging+prod (single VM + nginx? PaaS w/ managed MySQL? K8s?), domains, TLS provisioning, and DB hosting responsibility (MSH/DEP evidence: no compose/infra exists).
- **Impact:** decides P6-02..09 shape, backup tooling, monitoring stack, single-node constraints.
- **Default:** smallest viable: single VM + Docker Compose + managed backups, revisit scale later. Decide-by: before P6-01 (blocks critical path!).
- **Outcome (decided 2026-10-25, user-approved):** **(a)-superseded.** Target: application = container; database = **Supabase (PostgreSQL)**; file storage = **Supabase Storage (S3)** with bucket policies. Staging = same shape (separate Supabase project/keys). Consequences: assumption A-4 (MySQL 8 final) **superseded** — migration tasks **[ADD-01]** (adopt Postgres; Jira EB-81) and **[ADD-02]** (MySQL→Postgres schema/data migration; Jira EB-82) precede `P3-01`'s Flyway baseline, which must be captured on Postgres. Infra tasks P6-02/03/05 re-scoped accordingly (uploads volume no longer required; secrets split into Supabase-managed DB creds + container env for app secrets).
- **Amendment (2026-11-02, user-approved):** the container's **host is Vercel**. The Spring Boot app stays fully dockerized: GitHub Actions builds and pushes the image to **GHCR** (GitHub Container Registry), and Vercel deploys/hosts the container per [Vercel's Dockerfile approach](https://vercel.com/blog/dockerfile-on-vercel) — registry auth + env-injected secrets (Supabase DB, SMTP, Zoom; Stripe keys after [ADD-06]). Supersedes the "single VM + Docker Compose" default. Consequences: P6-02 "host provisioning" → Vercel project + GHCR pipeline wiring; P6-04 deploys the pushed image (no VM scripts); P6-05 uploads volume not applicable — object storage ([ADD-03]/D-25) is the only file persistence. Platform constraints (WS/STOMP persistence, background schedulers) must be validated before phase-06 exit — risk **R14**.
- **Second amendment (2026-11-02, user-approved — team decision):** **Vercel is pivoted out to Railway** (fallback: Render) — R14's analysis is confirmed as an architectural mismatch: Vercel's serverless/container model does not serve a traditional Spring Boot app's STOMP WebSocket persistent connections or `@Scheduled` background tasks reliably, and the live-session feature depends on both. **Railway natively runs long-lived Docker containers**, matching the app's shape. Consequences: P6-02 "host provisioning" = Railway project + service config (Dockerfile deploy from GitHub); P6-04 deploy targets Railway (GHCR-image deploy path still valid — Railway can deploy from registry as well as repo); P6-05 volume question re-opens (Railway volumes exist) but object storage remains per D-25/[ADD-03]. **The Vercel amendment above is retained as the documented alternative/host-fallback** (kept "until another decision" per PO) — do not delete its Vercel-specific text; treat Railway as the default target and Vercel as the recorded contingency + GHCR path kept in both.
- **Ratification (2026-11-06, user-approved — EB-72 / P6-01):** **Railway deployment active at `https://backend.edunest.dpdns.org/`**. Decoupled continuous deployment ratified using dedicated release branch `production` connected to Railway, preventing redundant deployments on active `main` development. Full topology, environment dictionary, and Render/Vercel contingency runbooks codified in [`ops/environments.md`](../ops/environments.md). Resolves `DEP-07` and executes `D-22`.

### D-23 — CORS origins
- **Question:** Which origins whitelist in prod (SEL-11)?
- **Default:** FE + admin origin(s) the team provides; credentials stays `true`, wildcard never combined with credentials. Decide-by: before P1-23.
- **Outcome (decided 2026-10-31, user-approved):** **Public — no origin allowlist for now** ("make it public, no problem"). Implementation note: auth travels in the `Authorization` header (post-EB-11, no cookies/credentials), so credentialed CORS is not required; wildcard (`*`) is permitted **without** `allowCredentials=true`. Safety note on record: the moment any cookie/session-based request appears, wildcard must be dropped and this decision revisited. Unblocks `P1-23` (Jira **EB-30**) with scope: permissive open policy, no allowlist.

### D-24 — OSIV disable timing
- **Question:** When to disable Open-Session-In-View (DB-06)?
- **Default:** after P4-11 fetch-join program completes + no-OSIV test profile achieves green. Decide-by: before P3-06.

### D-25 — Upload storage
- **Question:** Docker volume now, object storage now, or abstract-then-migrate (FILE-07/DEP-03)?
- **Default:** volume now + abstraction task post-launch. Decide-by: before P6-07.
- **Outcome (decided 2026-10-25, user-approved):** **object storage now** — Supabase Storage (S3) is the chosen design and `P6-07`'s condition is resolved; the task is superseded by **[ADD-03]** (Jira EB-83), which includes retention/deletion policy and migration of any existing files. A Docker volume for uploads is no longer needed (scope removed from `P6-05`).

### D-26 — Error-body disclosure
- **Question:** Confirm generic bodies for unexpected errors (SEL-14 fix) — FE impact on error UX?
- **Default:** generic 500 body + keep field-validation detail map. Decide-by: before P1-05.

### D-27 — Test DB engine (no-Docker policy)
- **Question:** Testcontainers MySQL or H2 for the test profile (P2-01)?
- **Default (plan original):** Testcontainers MySQL ("native SQL rules out H2" — rationale written before verification).
- **Outcome (decided 2026-10-28, user-approved):** **H2 in-memory only; Docker is NOT allowed** (team no-Docker policy per Omar). The blocking rationale was re-verified and found overstated: exactly **one** special query exists (`TotalPointsRepository` rank list, `ROW_NUMBER() OVER`), and H2 2.x supports it — proven by the EB-35 smoke probe. Mitigations already in place: H2 `MODE=MySQL` compatibility, the window-function smoke test, and the rank query gets real-data fixtures in P2-02; if some future corner case fights H2, fallback = rewrite that single query in portable JPQL (never reintroduce Docker). CI note: EB-41 needs no special runner because tests are fully self-contained.
- **Application to EB-7 (ruled 2026-10-31):** the EB-7 rehearsal request for an isolated Docker/MySQL environment is **denied** under this decision. The fresh-database rehearsal runs on **H2 in-memory (MySQL-compat mode)**: scripted schema recreate + seeded-dump reset between rehearsal steps; "old security behavior before the fix" is evidenced via git-history diffs (`before` refs checked out in the rehearsal run) + rehearsal logs, all inside `mvnw test`. No Docker, ever.

### D-28 — Payment gateway (Stripe) *(decided 2026-11-02, user-approved)*
- **Question:** Adopt a third-party payment gateway for enrollment payments (invoicing/refunds surface exists via `AdminPaymentsController` but no external processor)?
- **Outcome:** **Stripe adopted.** Addendum **[ADD-06]** = ticket [P4-18] (Jira **EB-108**): server-created Stripe Checkout sessions for enrollment payments + signature-verified, idempotent webhooks (`checkout.session.completed` / `payment_intent.succeeded` + dispute/refund events) mutating the enrollment-payment state; amounts/currency taken **only** from server-side enrollment records, never client input; keys (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`) via the P6-03 env store; dev/test mode first, live keys gated to P7 provisioning. Scope intentionally limited to one-time enrollment payments — subscriptions and payout/commission automation are deferred (new ISSUES rows if they surface). Execution lands after sprint 2 (backlog ticket; candidate for Sprint 3+).

### D-29 — Zoom Basic plan & integration constraints *(decided 2026-11-02, user-approved)*
- **Question:** How to reconcile Zoom integration (EB-66 / P4-17) with the free/Basic plan constraints (40-minute meeting limit, 100 participants) and zero additional payment at current time?
- **Outcome:** **Basic plan accepted temporarily.** Server-to-Server OAuth app credentials (ZOOM_ACCOUNT_ID, ZOOM_CLIENT_ID, ZOOM_CLIENT_SECRET) provisioned via [ADD-05] / EB-85. Architecture:
  1. Meetings created via API are hard-capped at 40 minutes (pp.zoom.max-meeting-minutes: 40), with a warning logged if higher duration is requested.
  2. Legacy Jitsi room link generation is retained as a zero-cost fallback when pp.zoom.enabled=false.
  3. When upgraded in the future, only pp.zoom.* properties change with zero code refactoring required.
