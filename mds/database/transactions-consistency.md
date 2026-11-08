# Transactions & Consistency

Verified transactional behavior, race conditions, and rollback/visibility quirks across services.

## 1. Transactional inventory (spot-map)

| Style | Where | Notes |
|---|---|---|
| Class-level `@Transactional` | `NotificationService` (readOnly overrides on reads), `Service/services in mentorship/auth/ledger paths` | Common pattern |
| Method-level `@Transactional` | most mutation methods (grading, status transitions, enrollment, quiz submit) | jakarta or spring annotation mixed |
| `@Transactional @Async` | `NotificationService#sendToMentorshipStudents`, `#sendToAdmin` | async fan-outs still transactional via proxy |
| `@Transactional(readOnly=true)` | read aggregations | present in some; **absent** in several quiz/mentorship dashboard reads (OSIV covers) |
| **Resolved (EB-45)** | `WeekService.deleteWeek`, `WeekService.createWeek` | Decorated with `@Transactional` (EB-45); child cascade deletions/creates roll back atomically |

## 2. Write flows with known consistency characteristics

### 2.1 Enrollment (join)
- Single TX: exists-check → insert `Enrollment` → insert zero `TotalPoints` → notification (pre-commit).
- **Dedupe is incidental:** the second concurrent join dies on the `uk_student_mentorship` unique of the *points* insert, not the enrollment table (no unique there) → client sees a 500-shaped failure instead of "already enrolled" (MSH-04/DB-04).
- Commission lookup (`CommissionService`) inside the TX; empty `commission` table fails the join (`Commission rate not set`).

### 2.2 Points mutations
- **Merged main baseline:** `applyDelta` reads the ledger row with a pessimistic lock; the absent-row race and badge bulk update without clamp remain EB-51 concerns.
- **EB-51 implementation verified locally (unreleased):**
  - **Versioned Persistence Architecture (V8 & V9):** dedicated `@Version Long version` columns on `Badge` and `TotalPoints` with Flyway migration `V8__badge_and_total_points_versions.sql` (non-destructive backfill of `version = 0`). Flyway migration `V9__unique_session_attendance_result.sql` establishes database-level idempotency by creating unique index `uk_session_attendance_result_session_student` on `session_attendance_result(session_id, student_id)` and archiving duplicates into `eb51_archive_session_attendance_result`.
  - **Live points awarding idempotency:** `DefaultLivePointsAwarder` validates existing attendance results via `findBySession_IdAndStudent_Id` before applying point deltas, guaranteeing that duplicate sweep execution or manual/sweep races cannot double-credit live attendance points.
  - **Managed version-aware writes:** ledger mutations operate exclusively through managed `TotalPoints` instances obtained via `findForUpdate(studentId, mentorshipId)` and clamped in Java (`Math.max(0, current + delta)`). Stale entity flushes can never overwrite newer balances because Hibernate enforces optimistic version verification at flush/commit.
  - **Insert-only PostgreSQL initialization:** concurrent first-inserts are resolved safely using native SQL `INSERT INTO total_points (student_id, mentorship_id, total_points, version) VALUES (?, ?, 0, 0) ON CONFLICT (student_id, mentorship_id) DO NOTHING`, immediately followed by managed entity retrieval.
  - **Complete elimination of unmanaged bulk writes:** the unsafe `applyBadgePointDiff` bulk `@Modifying UPDATE` on `TotalPoints` was removed entirely. Badge point updates iterate over distinct awarded students in deterministic ascending student ID order, invoking `totalPointsService.applyDelta` to ensure version coherence, zero-clamping, and deterministic row locking.
  - **Locking hierarchy (L0 → L4):**
    1. L0: Mentorship gate in ascending ID order (`mentorShipRepository.findForUpdateById`).
    2. L1: Week gate (`weekRepository.findForUpdateById`).
    3. L2: Content/session gate (`taskRepository`, `projectRepository`, `quizRepository`, `sessionRepository` via `findForUpdateById`).
    4. L3: Submission/award gate (`quizSubmissionRepository.findByStudentIdAndQuizIdForUpdate`).
    5. L4: TotalPoints ledger rows in deterministic ascending student ID order.
  - **Dialect-agnostic entity navigation locking:** to eliminate Spring Data JPA interface projection alias inconsistencies across different SQL dialects (e.g. PostgreSQL 17 vs H2 in-memory), locking flows navigate relationships directly via managed entities (`task.getWeek().getId()`, `project.getWeek().getId()`) to acquire locks in canonical order: L1 (Week) -> L2 (Content) -> L3 (Submission).
  - **Complete elimination of `entityManager.refresh()`:** destructive `entityManager.refresh()` calls were completely purged from `BadgeAwardService`, `BadgeService`, `TaskServiceImpl`, `ProjectServiceImpl`, `TaskSubmissionServiceImpl`, and `ProjectSubmissionServiceImpl`. Authoritative row locking is achieved via dedicated `@Lock(LockModeType.PESSIMISTIC_WRITE)` methods (`badgeRepository.findForUpdateById`, `taskRepository.findForUpdateById`, `projectRepository.findForUpdateById`, `taskSubmissionRepository.findForUpdateById`, `projectSubmissionRepository.findForUpdateById`, `weekRepository.findForUpdateById`), preserving caller-managed state and preventing dirty flush loss.
  - **Live session sweep deterministic sorting:** expired live sessions in `LiveSessionSweepService` are deterministically pre-sorted by `(mentorshipId, weekId, sessionId)` in ascending order, eliminating cyclic lock waits across mentorships and weeks within sweep transactions.
  - **Authoritative conflict detection:** `BadgeLoadedState` extracts Hibernate `EntityEntry.getLoadedState()` snapshots and verifies authoritative database values via raw JDBC reads without triggering unwanted entity refreshes or discarding caller uncommitted changes. Same-field conflicts return HTTP 409 `ERR_CONCURRENT_MODIFICATION` with clean `ApiError` JSON payloads.
  - **All 4 mandatory PostgreSQL regressions verified passing:**
    1. `badgeEditPreservesCallerChangesAlreadyManagedByOuterTransaction`: caller dirty state preserved without destructive `refresh()`.
    2. `atomicDeltaSurvivesLaterFlushOfPreviouslyManagedLedgerRow`: managed entity updates survive subsequent persistence context flushes.
    3. `atomicDecrementSurvivesLaterFlushOfPreviouslyManagedLedgerRow`: managed entity decrement survives subsequent flushes.
    4. `badgeTitleConflictIsRejectedRatherThanOverwritingConcurrentCommit`: concurrent same-field updates rejected with `ConcurrentModificationConflict` (HTTP 409).

### 2.3 Grading, lifecycle & freeze protections
- **Task/project content point & deletion freeze:** editing points or deleting tasks/projects is rejected when submissions exist (`taskSubmissionRepository.existsByTask_Id`, `projectSubmissionRepository.existsByProject_Id`), conservatively protecting ambiguous histories.
- **Week cascade-delete protection:** `WeekService.deleteWeek` acquires the L1 gate lock (`findForUpdateById`) and verifies that:
  1. No live session in the week is currently `LIVE` (`sessionRepository.existsByWeek_IdAndStatus(weekId, SessionStatus.LIVE)`).
  2. No session attendance records exist under the week (`attendanceRepository.existsBySession_Week_Id(weekId)`).
  3. No task submissions (`existsByTask_Week_Id`), project submissions (`existsByProject_Week_Id`), or scored quiz attempts (`existsByQuiz_Week_IdAndScoreIsNotNull`) exist.
  Violations reject deletion immediately, preventing orphaned cascade deletes or active session corruption.
- **Quiz status transition & question lifecycle protection:**
  - `QuizServiceImpl.changeStatus` is transactional, acquires L1 (Week) and L2 (Quiz) locks, and enforces:
    - Immutable `CLOSED` state: once a quiz is `CLOSED`, it cannot transition to any other status.
    - Reverting to `DRAFT` is rejected if quiz submissions exist.
    - Publishing (`PUBLISHED`) is rejected if the quiz contains zero questions (`!questionRepository.existsByQuiz_Id(quizId)`).
  - `QuestionServiceImp`: `createQuestion`, `updateQuestion`, and `deleteQuestion` are transactional and acquire L1 (Week) and L2 (Quiz) locks, strictly rejecting question modification or deletion if the quiz is `PUBLISHED` or `CLOSED`, preventing reparenting and content manipulation during or after live examination windows.
- **Scored-quiz lifecycle protection:** `QuizServiceImpl.deleteQuiz` acquires L1 and L2 gates and blocks deletion if scored quiz submissions exist (`existsByQuiz_IdAndScoreIsNotNull`). Updating published or closed quizzes is strictly rejected.
- **Quiz double-submission protection:** `QuizSubmissionServiceImpl` holds the L2 quiz gate and serializes the submission row via L3 pessimistic lock (`findByStudentIdAndQuizIdForUpdate`), eliminating the duplicate award race window.
- **Badge post-completion freeze (CERT-04):** `BadgeService.updateBadge` rejects points modifications when the mentorship status is `COMPLETED`; `BadgeAwardService.awardBadge` rejects new badge awards when the mentorship status is `COMPLETED`, preserving certificate snapshot consistency.

### 2.4 Completion → certificates
- **EB-54 merged (PR #93):** issuance materializes one database-statement snapshot ordered by points descending and student ID ascending, including rank, points and cohort size. Certificate and notification records share the caller's transaction. Delivery uses synchronous after-commit callbacks with bounded reads; failures are logged and there is no durable retry/outbox. Historical certificate snapshot fields can be null. Flyway V7 adds the nullable fields and issuance metadata; it does not reconstruct historical snapshots.
- `GET /api/v1/student/certificates` returns `CertificateResponse.pointsAtIssuance` and `cohortSizeAtIssuance` as nullable values; ranks and these values describe issuance time, not a live leaderboard. The certificate notification retains its `NotificationDto` shape and `/queue/notifications` destination.
- EB-54 also corrects distinct-student counts for a mentor, excludes unsubmitted quiz attempts from quiz averages, counts scored published quizzes for student progress, and counts submitted task rows for pending review. The PostgreSQL certificate and migration tests run against a local Testcontainers database; they do not establish that any live environment has applied V7.

### 2.5 Read-only legacy submission audit (EB-51)

The following PostgreSQL query is a **proposal, not executed against production**. Bind `?` as the mentorship's numeric ID. It returns counts, not student identities or balances; use a read-only connection authorized for the target database. `recorded_contribution` does not prove a historic balance is otherwise correct. The last category cannot be split into never-graded versus legacy grade-then-resubmit using these fields. No submission row is itself proof of a never-graded existing submission.

```sql
WITH scope AS (SELECT CAST(? AS bigint) AS mentorship_id), submissions AS (
  SELECT 'task' AS kind, s.points_applied, s.submission_status,
         s.raw_score, s.final_score, s.graded_at
    FROM task_submission s JOIN tasks t ON t.id = s.task_id
    JOIN weeks w ON w.id = t.week_id JOIN scope x ON x.mentorship_id = w.mentorship_id
  UNION ALL
  SELECT 'project', s.points_applied, s.submission_status,
         s.raw_score, s.final_score, s.graded_at
    FROM project_submission s JOIN projects p ON p.id = s.project_id
    JOIN weeks w ON w.id = p.week_id JOIN scope x ON x.mentorship_id = w.mentorship_id
), classified AS (
  SELECT kind, CASE
    WHEN points_applied IS NOT NULL THEN 'recorded_contribution'
    WHEN submission_status = 'GRADED' OR raw_score IS NOT NULL
         OR final_score IS NOT NULL OR graded_at IS NOT NULL
      THEN 'grade_evidence_contribution_unknown'
    ELSE 'ambiguous_history'
  END AS evidence FROM submissions
)
SELECT kind, evidence, COUNT(*) AS submissions
FROM classified GROUP BY kind, evidence ORDER BY kind, evidence;
```

To count content with **no extant submissions**, bind the same mentorship ID in each query: `SELECT COUNT(*) FROM tasks t JOIN weeks w ON w.id=t.week_id WHERE w.mentorship_id=? AND NOT EXISTS (SELECT 1 FROM task_submission s WHERE s.task_id=t.id)`; repeat with `projects p`, `project_submission s`, and `s.project_id=p.id`. Deleted history cannot be recovered by these queries. A trustworthy per-submission historical ledger or verified independent provenance would be needed to resolve ambiguous previous credit; Git commit dates alone are not deployment evidence.

#### Double-Credit Vulnerability Path
- In the pre-EB-51 codebase, `points_applied` was unpopulated on initial grading, and resubmitting a task/project cleared `finalScore` and `gradedAt`.
- If an ambiguous legacy submission was graded prior to Sprint 2 without `points_applied` recorded, the student received points in `TotalPoints`.
- Under the standard regrading formula:
  $$\Delta = \text{newScore} - (\text{pointsApplied} \neq \text{null} \mathrel{?} \text{pointsApplied} : 0)$$
  Because `pointsApplied` is null, the formula computes $\Delta = \text{newScore} - 0 = \text{newScore}$.
- Applying this delta to `TotalPoints` adds the score a second time, inflating student points (double credit).
- To prevent this, submissions are categorized as:
  1. `KNOWN_CREDIT`: `points_applied IS NOT NULL` (safe to calculate delta = `newScore - pointsApplied`).
  2. `KNOWN_FIRST_GRADE`: reliable provenance proves no earlier credit exists. *(Currently BLOCKED: no reliable automatic classifier exists in the database schema because resubmissions erased grading evidence, points_applied is null across both fresh and legacy rows, and Git commit dates are not deployment evidence).*
  3. `AMBIGUOUS_LEGACY_HISTORY`: legacy submission with `points_applied IS NULL` where prior credit cannot be established or excluded.
- **Approved Resolution (D-09a - Integrity First):**
  - **Enforcement:** `TaskSubmissionServiceImpl.grade` and `ProjectSubmissionServiceImpl.gradeProject` fail closed with `LegacySubmissionAuditRequiredException` (HTTP 422 `ERR_BUSINESS_RULE`) whenever `pointsApplied == null` on a previously graded submission (`status == GRADED || gradedAt != null || finalScore != null`).
  - **First-time Submissions Safe:** Fresh submissions (`pointsApplied == null && status == SUBMITTED && gradedAt == null`) are classified as first-time gradings and proceed normally to credit points without administrative blocking.
  - **Client Overrides Purged:** Direct client headers (such as `X-Audit-Baseline-Points`) are purged and strictly rejected.
  - **Administrative Reconciliation:** `PointsReconciliationService` provides trusted administrative reporting and controlled remediation without client-side tampering.

### 2.6 Chat get-or-create (`Conversation`)
- `findBetweenUsers → orElseGet(create)` **without** a transaction or unique constraint → duplicate conversations possible under concurrent first messages (CHAT-05).

### 2.7 OTP single-row convention
- Delete-then-insert per user; all OTP purposes share the row → issuing any new OTP destroys a pending RESET/DELETE row mid-flow (SEL-05, suspected race window in auth flow).

### 2.8 Live session sweep transaction & concurrency characteristics (D-15b Resolved: Option B)
- **Approved Architecture (D-15b Option B):**
  - `LiveSessionSweepService` acts as a **non-transactional dispatcher** running on the 5-minute cron (`@Scheduled`). It queries candidate expired sessions sorted deterministically by `(mentorshipId, weekId, sessionId)`.
  - Dedicated Spring component `LiveSessionSweepWorker` executes session closures within isolated transactional boundaries (`@Transactional(propagation = Propagation.REQUIRED)`).
  - **Failure Isolation:** Each session is processed in its own transaction within an isolated try-catch loop. An unhandled exception or deadlock on one session rolls back only that session, allowing all other expired sessions in the batch to commit successfully (Dimension 3).
  - **Canonical Ordered Locking:** Worker acquires locks in strict canonical order: L0 Mentorship (`mentorShipRepository.findForUpdateById`) $\to$ L1 Week (`weekRepository.findForUpdateById`) $\to$ L2 Session (`liveSessionRepository.findForUpdateById`).
  - **Double-Check Status Guard:** Authoritative session status is rechecked under the L2 row lock (`session.getStatus() == LIVE` or `SCHEDULED`). If a mentor manually called `endSession` concurrently, the sweep worker skips without re-evaluating attendance or awarding points.
  - **Database-Level Idempotency:** Flyway migration `V9__unique_session_attendance_result.sql` guarantees that duplicate attendance records are rejected at the schema level (`uk_session_attendance_result_session_student`).
  - **PostgreSQL 17 Testcontainers Concurrency Verification:** Tested and verified against PostgreSQL 17 in `TotalPointsPostgresIntegrationTest.liveSessionSweepAndManualEndSessionSerializeSafelyOnPostgres` and 10 architectural scenarios in `LiveSessionSweepArchitectureScenariosTest`.

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
