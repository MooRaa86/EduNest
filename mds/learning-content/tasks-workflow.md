# Tasks Workflow (create → submit → grade → points)

Verified flows from `service/tasks/*` with exact citations.

## 1. Create (mentor)

`TaskServiceImpl#createTask` (L61–99):
1. Validate `passPoints ≤ points` (both required ints).
2. Resolve `week`; mentor must own week's mentorship (`existsByIdAndMentor_Email` chain) — enforced.
3. Optional attachment → `FileStorageService.saveFile("task-attachment","task", msid, msid, file)` → `uploads/task-attachment/task-…-{uuid}.{ext}` (Tika-validated).
4. Notify students **only if** created as `PUBLISHED`.
5. Idempotent-ish status transitions via `PATCH/{id}/status` (DRAFT→PUBLISHED→CLOSED).

## 2. Student view

`GET /api/v1/task/{taskId}/student` → `TaskSubmissionRepository#findTaskWithSubmission` (L36–64) LEFT JOINs the student's own submission (`ts.student.email = :email`) — **but no enrollment gate** (asymmetric vs project twin, which calls `validateEnrolled`): any authenticated user sees task internals incl. `uploadedAttachmentPath` (LEARN-02).

## 3. Submit (student)

`TaskSubmissionServiceImpl#submit` (L88–165):
1. Task must be `PUBLISHED`; student must be **enrolled** (`isStudentEnrolledForTask`) — enforced.
2. `isLate = now.isAfter(task.dueAt)` (flag computed only when `dueAt` is set — legacy rows without a deadline keep `isLate = false`).
3. **Late rule (updated EB-53 / P4-04, 2026-11-11, grace `app.submission.late-grace-minutes` default 10)**: late + previous submission exists → **still rejected first** ("Deadline passed. You can't resubmit..."). Late *first* submission is accepted **only within the grace window** (`dueAt + 10 min`), flagged `isLate=true`; **no automatic grade deduction** (mentor grading is untouched). Beyond grace → `globalLogicEx("Submission deadline plus grace period has passed. This task no longer accepts first submissions.")` — rejected before any row/points mutation.
4. One row per (task, student) via DB unique constraint; resubmission **overwrites** the same row: old file deleted, new saved; grade fields reset to null; `status = SUBMITTED`.
5. ⚠️ Resubmission leaves stale `pointsApplied` + `TotalPoints` until the mentor re-grades (delta math self-corrects then) — LEARN-06 window.
6. `fileUrl` request param is **not validated** (`SubmitTaskRequest` DTO unused).
7. Notify mentor (`sendToUserByEmail` "New Task Submission").

## 4. Grade (mentor)

`TaskSubmissionServiceImpl#grade` (L193–230):
1. Mentor owns the submission's chain (`validateMentorOwnsSubmission`) — enforced everywhere it matters.
2. Score bounds: 0 (DTO `@Min`) to `task.points` (max 1000 in DTO).
3. Sets `rawScore = task.points` (**mislabeled** — it stores max, not pre-adjusted score), `finalScore = req.score`, `status = GRADED`, `gradedAt = now`.
4. Points delta: `newScore − pointsApplied` → `TotalPointsServiceImp#applyDelta` (pessimistic-locked) → saves `pointsApplied = newScore`. **Re-grading is idempotent** for points.
5. Notify student ("Task Graded").
6. `dueAt` never blocks grading (mentor can grade late submissions).

## 5. Statistics (mentor)

`GET /{taskId}/statistics` → `TaskServiceImpl#getTaskStatistics`:
- `pendingReview = totalStudents − submissionsPage.getTotalPages()` — **counts PAGES, not submissions** (L272). Project twin is correct. Flagged LEARN-04.

## 6. Deletion & content changes

- `deleteTask` (L157–167): cascades submissions + deletes files; **does not revoke TotalPoints** → students keep points from deleted content (LEARN-06b).
- `updateTask` (L116–141): `points`/`dueAt` freely editable even after grading, with **no points recalculation** — mentor can set points=1 after a student earned 100 (LEARN-06a).
- `passPoints`: validated at create/echoed in DTOs, **never compared to any score** — no unlock behavior (LEARN-08).

## 7. SubmissionStatus values actually used

`SUBMITTED` → `GRADED` only. `CLOSED`, `IN_PROGRESS` exist in `enums/tasks/SubmissionStatus` but are never written (transition matrix dead-end).

## 8. Test recommendations

1. Submit → resubmit same row (file replaced, fields reset); late resubmit rejected; late first allowed (document decision). — **Decision D-08 implemented 2026-11-11 (EB-53/P4-04)**: late first submissions accepted only within the 10-min grace, beyond grace rejected; the same policy applies to the project twin (`ProjectSubmissionServiceImpl#submit`, using `project.endAt`). Probes: `QuizLatePolicyPostgresTest` (on-time / within-grace / beyond-grace / late-resubmit cases for both tasks and projects).
2. Grade above `task.points` rejected; re-grade deltas exact; deleted task doesn't crash student endpoints.
3. Ownership: student A cannot read/grade/etc. cross-student data; un-enrolled cannot submit (already enforced).
4. Statistics math after fix (or *while* noting the bug as a regression guard).
