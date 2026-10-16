# Projects Workflow (brief → submit → grade)

Mirrors tasks with deadline semantics (`startAt`/`endAt`) and a different resource shape. Verified from `service/projects/*`.

## 1. Create (mentor)

`ProjectServiceImpl#createProject` (L89–128): week + mentor ownership enforced; `endAt > startAt` validated; attachment → `uploads/project-attachment/project-…-{uuid}.{ext}`; status default DRAFT; transitions `PATCH /{id}/status` (DRAFT→PUBLISHED→CLOSED); notify on publish.

## 2. Student view

`GET /api/v1/project/{projectId}/student` — **enforcement present** (`validateEnrolled(projectId, email)` — unlike the task twin). `GET /{id}` and `/filter/{msid}` remain unguarded (LEARN-02).

## 3. Submit (student)

`ProjectSubmissionServiceImpl#submit` (L65–135):
1. Requires PUBLISHED + enrollment (`isStudentEnrolledForProjectByEmail`) — enforced.
2. `isLate = now.isAfter(project.endAt)`; late **resubmission** rejected; late **first** submission accepted+flagged (same asymmetry as tasks).
3. Unique row per (project, student); resubmission overwrites, resets grade fields, deletes old file.
4. `fileUrl` param unvalidated; file → `uploads/submissions/project-…` (Tika-validated).
5. Notify mentor.

## 4. Grade (mentor)

`ProjectSubmissionServiceImpl#gradeProject` (L162–192): mentor-email ownership check; `score ≤ project.points`; sets `rawScore = project.points` (mislabeled), delta-based `TotalPoints` update via `applyDelta` (`pointsApplied` bookkeeping); notify student.

## 5. Statistics

`ProjectServiceImpl#getProjectStatistics` (L309): `pendingReview = totalStudents − totalSubmissions` — **correct** (contrast with task twin's page-count bug).

## 6. Data notes

- Projects reuse `SubmissionStatus` enum; the project's deadline (`endAt`) is never auto-enforced either — same soft-late model.
- Graded submissions double as portfolio items on the public/student profile surfaces (`StudentProfileService`, `StudentAchievementService`).

## 7. Test recommendations

1. Deadline edge: submit exactly at endAt boundary (inclusive `isAfter`), resubmission rules, file replacement.
2. Points delta across re-grade and after mentor edits `project.points` (document current behavior).
3. Enrollment edge: un-enrolled submit rejected (already enforced — regression guard).
