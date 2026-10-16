# Transactions & Consistency

Verified transactional behavior, race conditions, and rollback/visibility quirks across services.

## 1. Transactional inventory (spot-map)

| Style | Where | Notes |
|---|---|---|
| Class-level `@Transactional` | `NotificationService` (readOnly overrides on reads), `Service/services in mentorship/auth/ledger paths` | Common pattern |
| Method-level `@Transactional` | most mutation methods (grading, status transitions, enrollment, quiz submit) | jakarta or spring annotation mixed |
| `@Transactional @Async` | `NotificationService#sendToMentorshipStudents`, `#sendToAdmin` | async fan-outs still transactional via proxy |
| `@Transactional(readOnly=true)` | read aggregations | present in some; **absent** in several quiz/mentorship dashboard reads (OSIV covers) |
| **Missing entirely** | `WeekService.deleteWeek`, `LiveSessionServiceImp#startLiveSession`/`#recordSnapshot` | works via repository-level implied transactions today; multi-write steps are not atomic by design (DB-05) |

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
| `WeekService.deleteWeek` | cascade-delete contents (DB) + delete attachment files | files deleted even if DB op fails (or vice versa); with no outer TX, a partial failure can orphan file references — no reconciliation job exists |
| `updateCoverImage` mentorship | delete old file → save new → update entity URL | failure between steps leaves URL→missing file (FILE-05) |
| `recordSnapshot` | validate ids + N×`save()` in loop | slow/failed midway leaves partial snapshot sets (attendance math is snapshot-count based) |
| Image profile replace | delete old + save new | same failure-window pattern as cover images |

## 5. Repair recommendations (priority order)

1. Add missing DB unique constraints (`enrollments`, `mentorship_reviews`, `conversation(user1,user2)`) and handle constraint violations as friendly errors (DataIntegrityViolation handler already maps them: [../api/error-responses.md](../api/error-responses.md)).
2. Route badge bulk-update through `applyDelta`-equivalent locking (or clamp + lock inside the JPQL).
3. Introduce Flyway baseline; stop trusting `ddl-auto=update` (DB-01).
4. Wrap the multi-step file-vs-DB flows in transactions with **afterCommit** file operations (inverse of today's file-first order) to guarantee URL/file existence.
5. Decide and apply soft-delete filters centrally (a Hibernate `@Where` or query-layer rule) once product intent is confirmed (DB-03).
