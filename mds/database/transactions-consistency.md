# Transactions & Consistency

Verified transactional behavior, race conditions, and rollback/visibility quirks across services.

## 1. Transactional inventory (spot-map)

| Style | Where | Notes |
|---|---|---|
| Class-level `@Transactional` | `NotificationService` (readOnly overrides on reads), `Service/services in mentorship/auth/ledger paths` | Common pattern |
| Method-level `@Transactional` | most mutation methods (grading, status transitions, enrollment, quiz submit) | jakarta or spring annotation mixed |
| `@Transactional @Async` | `NotificationService#sendToMentorshipStudents`, `#sendToAdmin` | async fan-outs still transactional via proxy |
| `@Transactional(readOnly=true)` | read aggregations | present in some; **absent** in several quiz/mentorship dashboard reads (OSIV covers) |
| Method-level write boundaries | `WeekService.deleteWeek`, `LiveSessionServiceImp#startLiveSession`/`#recordSnapshot` | Week deletion is transactional; the live-session paths were already transactional when P3-03 was implemented. |

## 2. Write flows with known consistency characteristics

### 2.1 Enrollment (join)
- Single TX: exists-check → insert `Enrollment` → insert zero `TotalPoints` → notification (pre-commit).
- **Dedupe is incidental:** the second concurrent join dies on the `uk_student_mentorship` unique of the *points* insert, not the enrollment table (no unique there) → client sees a 500-shaped failure instead of "already enrolled" (MSH-04/DB-04).
- Commission lookup (`CommissionService`) inside the TX; empty `commission` table fails the join (`Commission rate not set`).

### 2.2 Points mutations
- `applyDelta` path: pessimistic `@Lock(PESSIMISTIC_WRITE)` row fetch (`findForUpdate`), delta, clamp≥0 — safe against lost updates and regrade races.
- Badge **bulk** update (`updatePointsForBadgeAwards` `@Modifying`): bypasses both the lock and the clamp — can drive totals negative; runs in the badge-edit TX (GAME-03).
- Quiz award truncates `Double → int` (informational).

### 2.3 Grading & resubmission
- Grade TX: submission fields + points delta — **regrade-safe** (`pointsApplied`).
- Resubmission resets grade fields but *keeps* `pointsApplied` until next re-grade (window where `TotalPoints` reflects a stale score; self-corrects) — LEARN-06.

### 2.4 Completion → certificates
- Status TX does: certificate `saveAll` + N relations + N notifications + admin notification. Certificates are rows only; rollback would remove them but *pre-commit pushes* would have already reached live clients (NOTIF-03 ghosting).

### 2.5 Chat get-or-create (`Conversation`)
- `findBetweenUsers → orElseGet(create)` **without** a transaction or unique constraint → duplicate conversations possible under concurrent first messages (CHAT-05).

### 2.6 OTP single-row convention
- Delete-then-insert per user; all OTP purposes share the row → issuing any new OTP destroys a pending RESET/DELETE row mid-flow (SEL-05, suspected race window in auth flow).

## 3. Open-Session-In-View (OSIV) reliance

Spring Boot's default OSIV keeps the session open per request; the following verified paths navigate lazy state **outside** explicit transactions and would break `LazyInitializationException` or re-query per item if OSIV were disabled:

- `mentorShipServiceI.mapToMentorShipResponse` walking `whatWillLearn` per row (also the source of the MSH-08 N+1).
- Quiz dashboard/detail reads navigating `quiz.getQuestions()` without `@Transactional(readOnly)` on the calling method.
- Dashboard services reading lazy projections through repository aggregates.

Implication: build a **no-OSIV test profile** before attempting transactional refactors; document explicitly which reads must become fetch-joins.

## 4. Multi-step operations without atomicity

| Operation | Steps | Risk |
|---|---|---|
| `WeekService.deleteWeek` | transactionally cascade-delete contents; schedule task/project attachment and submission-file deletion after commit | rollback retains records and files; a post-commit storage failure can leave an orphan requiring reconciliation |
| Mentorship cover and profile/chat image replacement | save new → update reference → delete old after commit | transaction rollback removes the new local/S3 object; post-commit cleanup is best effort |
| `recordSnapshot` | validate ids, collect attendance rows, then `saveAll()` in a transaction | invalid or failed writes roll back together |
| Task/project attachments and submissions | save new → update reference → delete old after commit | rollback preserves the old reference and file; new local/S3 uploads receive rollback cleanup |

File synchronization requires an active Spring transaction. Outside one, deletions remain immediate. Cleanup is best effort, not a distributed transaction: process failure, an unknown commit outcome, or a storage error can leave orphaned files, so operators must monitor cleanup errors and reconcile manually. The old file is deliberately not removed on rollback.

### 4.1 Zoom meeting lifecycle (EB-45)

`LiveSessionServiceImp.createSession` must create the Zoom meeting before the database commit to obtain the meeting ID and URLs. A known database rollback triggers best-effort deletion of the newly created meeting; compensation failures are logged with the meeting ID and never replace the original error. An unknown transaction outcome is logged for reconciliation rather than risking deletion of a meeting belonging to a committed session.

Updates, deletes, and endings initiated by `LiveSessionServiceImp` are sent to Zoom after a successful database commit. Zoom failures are logged for manual reconciliation and do not change the already-committed API result, matching the previous best-effort failure contract. These in-process callbacks are not durable: a process crash after commit, a Zoom timeout with an unknown remote outcome, or a failed compensation can leave the systems inconsistent. No automatic retry or outbox is provided. The separate scheduled `LiveSessionSweepService` also calls Zoom inside its transaction and remains outside this change; its remote side effects can survive a database rollback.

## 5. Repair recommendations (priority order)

1. Add missing DB unique constraints (`enrollments`, `mentorship_reviews`, `conversation(user1,user2)`) and handle constraint violations as friendly errors (DataIntegrityViolation handler already maps them: [../api/error-responses.md](../api/error-responses.md)).
2. Route badge bulk-update through `applyDelta`-equivalent locking (or clamp + lock inside the JPQL).
3. Introduce Flyway baseline; stop trusting `ddl-auto=update` (DB-01).
4. Wrap the multi-step file-vs-DB flows in transactions with **afterCommit** file operations (inverse of today's file-first order) to guarantee URL/file existence.
5. Decide and apply soft-delete filters centrally (a Hibernate `@Where` or query-layer rule) once product intent is confirmed (DB-03).
