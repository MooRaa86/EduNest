# Phase 04 — Tasks

Format canon: [`templates/TASK-TEMPLATE.md`](../../templates/TASK-TEMPLATE.md); anchors are short link targets (`tasks.md#p4-nn`).

**Trilogy order note:** P4-01/02/03 share hook patterns (`TransactionSynchronizationManager` afterCommit) — land separately, *sequentially*, each with its own tests.

<a id="p4-01"></a>
### P4-01 — Notification pipeline (afterCommit, guests, broadcast, cleanup)

**Priority:** P1 — pushes fire inside uncommitted transactions (rolls back → ghost notifications), guests 404-receive pushes, broadcasts always hit only the newest admin (NOTIF-02/03/04, USR-05, D-12).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** NOTIF-02, NOTIF-03, NOTIF-04 (=USR-05), NOTIF-05; reuse of P3-03 hook pattern   **Domain:** notifications
**Components:** `NotificationServiceImpl#sendNotification` callsites (10+), `ContactMessageService` guest branch (guard-or-omit), admin-notification targeting, unused projections cleanup coordination with P1-26

**Evidence:** ISSUES NOTIF rows; [`mds/notifications/MDS.md`](../../../mds/notifications/MDS.md).
**Expected outcome:** pushes only after successful commit (afterCommit hook); guest contact submission never attempts a user-targeted push (=message actually marked saved instead of 404? — assert outcome: push is skipped without failing the request); admin notifications fan out to **all** active admins per D-12; unused `NotificationProjection`/`findSenderInfo` removed (if P1-26 hasn't yet).
**Approach:** extract `DeferredNotifications` helper (collect → afterCommit flush) shared with P4-02/03; keep payload contracts unchanged.
**FE / API impact:** none (delivery semantics only).
**Depends on:** D-12; P3-03 pattern.  **Blocks:** P4-02/03.
**Tests:** IT: notification absent on rollback; present after commit; fan-out-count assertions; guest-flow happy path.
**Docs:** notifications/MDS.md; ISSUE-MAPPING NOTIF rows.

<a id="p4-02"></a>
### P4-02 — Points ledger consistency & game integrity

**Priority:** P1 — bulk badge updates bypass locks with unbounded negative deltas (GAME-03), races hand out inflated points (GAME-04), and re-grading/editing silently breaks historical point math (LEARN-06, CERT-04 via D-09).  **Size:** L
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + QA
**Issues:** GAME-03, GAME-04, GAME-05, LEARN-06, CERT-04 (D-09 rules)   **Domain:** gamification / learning
**Components:** `updateBulkUserPoint`-equivalent methods (clamp≥0, `@Modifying` + pessimistic/identity update strategy), delta computations in grading paths, `TotalPoints` reconciliation on task/quiz edit/delete (per D-09: locked-after-grade default), badge-edit freeze check on completed mentorship (CERT-04)

**Evidence:** ISSUES GAME/LEARN-06 rowset; [`mds/database/transactions-consistency.md`](../../../mds/database/transactions-consistency.md); [`mds/gamification/MDS.md`](../../../mds/gamification/MDS.md).
**Expected outcome:** negative deltas clamp at 0; bulk updates single-statement (no lost updates); regrades recompute exactly once with idempotency guard; point-bearing content locks after first grade (D-09 default) — or the chosen rule; audit notes (`GAME-05`) reflected in code comments where deltas credited.
**Approach:** D-09 rules recorded first (task checklist documents chosen rule); ledger writes via `@Modifying` SQL increments (atomic); reconciliation helper recomputes user totals from rows for migration consistency; tests.
**FE / API impact:** freeze follows D-09; UI reads unchanged.
**Depends on:** D-09; P3-03 patterns; P2-02/04 suites to adapt.  **Blocks:** P4-03.
**Tests:** IT: concurrent bulk-update no lost/mis-accounted deltas; clamp; regrade idempotent; freeze on completed mentorship.
**Docs:** gamification/MDS.md + certificates/MDS.md; ISSUE-MAPPING rows.

<a id="p4-03"></a>
### P4-03 — Completion flow restructure

**Priority:** P1 — mentorship completion performs multi-service side effects (badges, certificates, mail, notifications) inline under one status-update transaction (MSH-10) — a failure mid-flow strands partial completion.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + Security Reviewer (certs touch auth-ish code paths? no, but sign-off for behavior)
**Issues:** MSH-10   **Domain:** mentorship-flow / notifications / certificates
**Components:** `MentorShipServiceImp` completion logic, `CertificateService` rank issuance, afterCommit hook usage from P4-01, reconciliation note

**Evidence:** ISSUES MSH-10; [`mds/mentorship-flow/mentorship-lifecycle.md`](../../../mds/mentorship-flow/mentorship-lifecycle.md).
**Expected outcome:** status transition atomic & idempotent (second call = no-op, no double-issued certificates/badges); side effects (badge award, certificate rank, email, notifications) run afterCommit with a reconciliation task pattern for stragglers (logged manual re-run documented).
**Depends on:** P4-01 hooks; D-09 freeze semantics from P4-02.  **Blocks:** —
**Tests:** IT: mid-hook failure → no partial rows; double-complete idempotent.
**Docs:** mentorship-lifecycle.md; ISSUE-MAPPING MSH-10.

<a id="p4-04"></a>
### P4-04 — Late-submission & quiz close policy

**Priority:** P2 — first attempts accepted late with full points (LEARN-05), quizzes stay open after their time window unless grading matchmaking depends on it (LEARN-10).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** LEARN-05, LEARN-10 (D-08)   **Domain:** learning-content
**Components:** submission service late-branch, properties (`app.quiz.late-grace-minutes`, penalty rule), `@Scheduled` quiz auto-close job, publish-time edit guard (PATCH on questions of a quiz that already closed-at opens → restrict per existing rule)

**Evidence:** ISSUES LEARN-05/10; [`mds/learning-content/quizzes-workflow.md §3–4`](../../../mds/learning-content/quizzes-workflow.md).
**Expected outcome:** late submissions flagged + full-points-or-penalty per D-08 (default: accept with `isLate=true` and the audit-noted −1pt/min rule up to grace, reject beyond); auto-close scheduler sets `ended` state at deadline + grace; questions locked after close.
**FE / API impact:** `isLate` field exposure contract (additive).
**Depends on:** D-08.  **Blocks:** —
**Tests:** IT: late-accept within grace − penalty; reject beyond; scheduler closes; edit-after-close rejected.
**Docs:** quizzes-workflow.md; ISSUE-MAPPING LEARN-05/10.

<a id="p4-05"></a>
### P4-05 — Stats, projections & certificate read fixes

**Priority:** P2 — a truthfulness pass on reported numbers: `LEARN-04` pendingReview miscount, opened-quizzes progress inflation (LEARN-07), `score` semantics (LEARN-09), `USR-03` projection quirks, `CERT-02` rank context, `CERT-03` unpaged issuance.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** QA + Eng Lead
**Issues:** LEARN-04, LEARN-07, LEARN-09, USR-03, CERT-02, CERT-03   **Domain:** learning-content / users-profiles / certificates
**Components:** instructor/review stats (`status==REVIEW`-only fix), quiz-progress counting (submitted-only), score persistence for points-on-quiz (LEARN-09 keeps `score` literal without badge-race), SimplfiedUserInfo projections (totalBadges=0 fix, enrollment-count), certificate ranking context (score+rank=nth display per CERT-02 note), paged issuance processing

**Evidence:** ISSUES rows; [`mds/learning-content/my-learning-and-achievements.md`](../../../mds/learning-content/my-learning-and-achievements.md), [`mds/certificates/MDS.md`](../../../mds/certificates/MDS.md).
**Expected outcome:** stats match DB reality (fixture: 3 submitted/1 opened → progress 3-count etc.); certificates issuance uses paging (no giant cursor-less scan); displayed rank context consistent.
**Depends on:** —  **Blocks:** —
**Tests:** IT fixtures per row certifying counts.
**Docs:** my-learning-and-achievements.md + certificates/MDS.md; ISSUE-MAPPING rows.

<a id="p4-06"></a>
### P4-06 — Chat reliability fixes

**Priority:** P2 — save failures drop silently mid-error (CHAT-01), edit/delete events broadcast to wrong room (CHAT-06), payload ambiguity from P0-03 (CHAT-08) resolved.  **Size:** S
**Owner role:** Backend Developer (WS)   **Reviewers:** WS reviewer
**Issues:** CHAT-01, CHAT-06, (CHAT-08 if P0-03 confirmed)   **Domain:** websocket
**Components:** `ChatMessageService` error path (persistence failure → explicit ERROR frame to sender, not silent), event destination derivation, String/Object contract per P0-03 result

**Evidence:** ISSUES rows; [`mds/websocket/messaging-flow.md`](../../../mds/websocket/messaging-flow.md).
**Expected outcome:** transient save failure → sender receives an ERROR frame they can retry; edit/delete events reach the room `chatroom/{id}`, not a sibling; content binding (str or object→JSON) fixed per FE payload.
**Depends on:** P1-12 (base authz), P0-03.  **Blocks:** —
**Tests:** WS IT: injected repo failure → ERROR frame; edit broadcast hits correct room only.
**Docs:** messaging-flow.md; ISSUE-MAPPING rows.

<a id="p4-07"></a>
### P4-07 — Chat receipts/unread (conditional)

**Priority:** P2 — CHAT-07's read-stat model is a real feature stub; **D-13 may defer** (default deferral post-launch).  **Size:** M
**Owner role:** Backend Developer (WS+repos)   **Reviewers:** Eng Lead + FE liaison
**Issues:** CHAT-07 (D-13)   **Domain:** websocket
**Components:** read-marker persistence (per-user readAt on messages), `/read` endpoint or event, unread counts endpoint (additive)

**Evidence:** ISSUES CHAT-07; websocket docs §gaps.
**Expected outcome (if approved):** unread counts truthful; receipts minimal (last-read); no message-content change.
**Depends on:** D-13 approval.  **Blocks:** —  **Conditional deferral:** record in RISKS/D-13 note if deferred.
**Tests:** WS/HTTP IT for marker lifecycle.

<a id="p4-08"></a>
### P4-08 — Live-session rules & auto-lifecycle

**Priority:** P2 — hardcoded attendance constants inline (LS-02) and sessions never auto-close (LS-03); plus `LS-05` missing transaction.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + PO (constants confirm)
**Issues:** LS-02, LS-03, LS-05 (D-15)   **Domain:** live-sessions
**Components:** constants → properties (`app.livesession.*`: threshold, snapshot grid, bonus, absence-floor), `@Scheduled` actualEnd/cancel sweep, `@Transactional` addition

**Evidence:** ISSUES rows; [`mds/live-sessions/MDS.md`](../../../mds/live-sessions/MDS.md) (5-min snapshot math from `LiveSessionServiceImp:394-481`).
**Expected outcome:** constants into config with identical current values; scheduled sweep closes ENDED after actualEnd (grace configurable) and handles orphan-joining; session writes transactional.
**Depends on:** D-15.  **Blocks:** —
**Tests:** IT with moved clock (mock bean): snapshots honored; scheduler closes; math unchanged (P2-02 suite asserts).
**Docs:** live-sessions/MDS.md; ISSUE-MAPPING rows.

<a id="p4-09"></a>
### P4-09 — API ergonomics: pagination caps & params

**Priority:** P2 — pagination surfaces accept unbounded or invalid sizes (`size=0`, negatives, 10000) and clamp it globally; plus the project-list `status` param that exists but doesn't filter (USR-01), profile-page caps (USR-04), chat-log cap (WS-03), notification query bounds (NOTIF-05) — one standardization pass.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** API-02, WS-03, USR-01, USR-04, (bounds for NOTIF-05)   **Domain:** api / users
**Components:** `@PageableDefault`/min-max clamps helper; `projectServiceImpl` status-param wiring; notification query caps

**Evidence:** ISSUES rows; [`mds/api/MDS.md`](../../../mds/api/MDS.md).
**Expected outcome:** global max page size (e.g., 50) clamps requests; invalid params produce 400 or clamp per decision (default clamp + doc note); `status` filter actually filters on projects endpoint.
**FE / API impact:** silent clamp documented (page-size consumers unaffected for normal sizes).
**Depends on:** —  **Blocks:** —
**Tests:** param-bound suite (shared with P2-06).
**Docs:** endpoints.md; ISSUE-MAPPING rows.

<a id="p4-10"></a>
### P4-10 — Explore/homepage perf & correctness

**Priority:** P2 — homepage loads *all* mentorships then sorts in memory (MSH-08), keyword `%`-injectable (MSH-12, P0-05 evidence), price filter matches base not effective price (MSH-07, default: filter on effective).  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** Eng Lead
**Issues:** MSH-08, MSH-12, MSH-07 (default adopted)   **Domain:** mentorship-flow
**Components:** `HomePageService#getAllMentorShips` (DB pagination + `EntityGraph` for reviews), search keyword escape, price filter join to effective-price computation

**Evidence:** ISSUES rows; [`mds/mentorship-flow/explore-and-homepage.md`](../../../mds/mentorship-flow/explore-and-homepage.md); P0-05 artifacts.
**Expected outcome:** homepage queries DB-paged (recorded before/after row counts + latency on dev dataset); keyword sql-escape with `ESCAPE` clause; explore filter uses computed effective price matching display.
**Depends on:** P0-05.  **Blocks:** P4-11 (same query surface but different scope).
**Tests:** IT: pagination correctness, escaping behavior, price-filter matrix (base vs discounted membership).

<a id="p4-11"></a>
### P4-11 — Fetch-join / N+1 program

**Priority:** P2 — the audit's repeated N+1 pattern list across explore/homepage/leaders/certs/chat participants, plus D-24's no-OSIV readiness gate.  **Size:** L
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + QA
**Issues:** DB-06's fix premise, MSH-08 deep rows   **Domain:** database / cross-cutting
**Components:** `@EntityGraph`/fetch-join sweeps on listed services (per P3-06 inventory), `spring.jpa.open-in-view=false` **test-profile trial run** gated by D-24

**Evidence:** ISSUES DB-06; [`transactions-consistency.md §3`](../../../mds/database/transactions-consistency.md) (the OSIV audit); P3-06 inventory.
**Expected outcome:** no-OSIV test profile **green** (ready to enable via D-24); N+1 hot spots resolved on inventoried paths; load test number recorded.
**Depends on:** P3-06, P4-10.  **Blocks:** D-24 decision point (scheduling note).
**Tests:** no-OSIV suite run (red list = what P4 fixes); green list at end.
**Docs:** transactions-consistency.md; ISSUE-MAPPING DB-06.

<a id="p4-12"></a>
### P4-12 — Batch persistence loops

**Priority:** P2 — per-row flush loops in attendee/assignment notifies (`DB-07`, `LS-04` ident) — modest change to `hibernate.jdbc.batch_size` + repository saveAll patterns.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Backend Lead
**Issues:** DB-07, LS-04   **Domain:** database / live-sessions
**Components:** batch settings, loopized saves → `saveAll` where safe

**Evidence:** ISSUES rows; [`mds/database/indexing.md` met](../../../mds/database/indexing.md) (perf context).
**Expected outcome:** batch config on; loops in live-session attendance and assignment-notification paths batched without behavior change.
**Depends on:** P4-08 (LS surface).  **Blocks:** —
**Tests:** IT: same outcome, no N+1 (verify row counts by one flush).

<a id="p4-13"></a>
### P4-13 — Email delivery observability

**Priority:** P2 — SMTP failures are invisible outside logs today (`INT-02`): outbox-style persistence for failed deliveries, admin visibility, and bounded retry.  **Size:** M
**Owner role:** Backend Developer   **Reviewers:** DevOps + Eng Lead
**Issues:** INT-02   **Domain:** integrations / notifications
**Components:** `EmailService` failure hook, `failed_email_attempts` table (Flyway migration), retry `@Scheduled`, minimal admin listing endpoint (admin-only)

**Evidence:** ISSUES INT-02; [`mds/integrations/MDS.md`](../../../mds/integrations/MDS.md).
**Expected outcome:** failed sends recorded with code/attempt count; bounded retries (e.g., 3 with backoff); admin can list/ack failures.
**Depends on:** CI green (migration).  **Blocks:** —
**Tests:** IT with a failing mail host: failure recorded, retried, capped.
**Docs:** integrations/MDS.md; ISSUE-MAPPING INT-02.

<a id="p4-14"></a>
### P4-14 — Jackson parity (API-05)

**Priority:** P3 — strict unknown-property handling exists only in prod; unifying avoids "works in dev, 400 in prod" surprises.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** Eng Lead + FE liaison
**Issues:** API-05   **Domain:** api
**Components:** dev profile Jackson property alignment (strictness both profiles)

**Evidence:** ISSUES API-05; [`mds/api/MDS.md`](../../../mds/api/MDS.md) dispatcher facts.
**Expected outcome:** both profiles strict (or both lenient per recorded choice with FE confirm — default strict, doc'd).
**Depends on:** FE note.  **Blocks:** —
**Tests:** contract probes across profiles.

<a id="p4-15"></a>
### P4-15 — `passPoints`/unlock (conditional)

**Priority:** P2 — checkpoint-unlock feature (LEARN-08 High severity is feature-parity, not a security hole; recall priority-mapping exception). **Default: defer; alternative: full implementation.**  **Size:** L (if implemented)
**Owner role:** Backend Developer   **Reviewers:** PO + Eng Lead
**Issues:** LEARN-08 (D-10)   **Domain:** learning-content
**Components:** unlock gate in fetch/submit paths, config per quiz, migration note (if field needs semantics), FE unlock UX

**Evidence:** ISSUES LEARN-08; [`quizzes-workflow.md §5`](../../../mds/learning-content/quizzes-workflow.md).
**Expected outcome (if approved):** blocked until prerequisite checkpoint passed (post-submission unlock); else: remove field + claims in P5-05 (doc task), testability note.
**Depends on:** D-10.  **Blocks:** —
**Tests:** lifecycle IT if built.
**Docs:** quizzes-workflow.md (either outcome); ISSUE-MAPPING row.

<a id="p4-16"></a>
### P4-16 — Envelope/path standardization (conditional)

**Priority:** P2 — `SimpleResponse` vs typed vs `PageResponse` consolidation (API-01) and legacy path normalization (API-03) — **expected deferral** per D-20 (breaking-change cost outweighs value pre-launch).  **Size:** L
**Owner role:** Backend Developer   **Reviewers:** FE liaison + Eng Lead
**Issues:** API-01, API-03 (D-20)   **Domain:** api
**Components:** response wrapper audit, path aliasing strategy if any

**Evidence:** ISSUES rows; api docs.
**Expected outcome:** recorded decision implementing the chosen optionality; deferral documented as the outcome if declined (D-20 row updated).
**Depends on:** D-20.  **Blocks:** —
**Tests:** contract suites updated accordingly (if implemented).

<a id="p4-17"></a>
### P4-17 — Jitsi JWT config hook (conditional)

**Priority:** P2 — LS-01's real fix is an *infrastructure decision* (D-16). This task only lands the *hook* if self-hosted JWT mode is chosen: optional JWT config wiring for room tokens.  **Size:** S
**Owner role:** Backend Developer   **Reviewers:** DevOps
**Issues:** LS-01 (D-16 conditional)   **Domain:** live-sessions / integrations
**Components:** `JitsiService` (optional-JWT properties), room creation config switch

**Evidence:** ISSUES LS-01; [`mds/integrations/external-services.md`](../../../mds/integrations/external-services.md).
**Expected outcome:** either docs+claims updated (default: stay public rooms) or JWT-mode feature flag wired (Undecided at decision — see D-16); 15-liner current service remains functional either way.
**Depends on:** D-16.  **Blocks:** —
**Tests:** config-mode probe if implemented.
