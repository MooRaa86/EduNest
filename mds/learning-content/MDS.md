# Learning Content (Tasks · Projects · Quizzes) — Primary Domain Document

## 1. Overview

The graded-work engine. Each course week contains tasks (file submissions, manually graded), projects (capstone submissions, manually graded) and quizzes (MCQ auto-graded at submit time, single attempt). All graded work awards points into the per-mentorship `TotalPoints` row, which feeds leaderboards and certificate ranks.

**Business value:** the actual learning checkpoint system and the points pipeline feeding gamification/certification.

**Scope:** `controller/tasks`, `service/tasks`, `entity/tasks`, `controller/projects`, `service/projects`, `entity/projects`, `controller/quiz`, `service/quiz/{quiz,question,submission}`, `entity/quiz`, plus the student aggregation surfaces (`controller/mylearning`, `controller/studentAchievementController`).

## 2. Functional Requirements (as implemented)

| # | Requirement | Status |
|---|---|---|
| R1 | Mentor CRUD of tasks/projects with attachments + statuses | Implemented |
| R2 | Student file-based submissions with Tika-validated storage | Implemented |
| R3 | Mentor grading with 0..max score + feedback + points delta | Implemented (task + project) |
| R4 | Resubmission unique-row overwrite + late flag | Implemented (rules below) |
| R5 | Quiz authoring (A–D MCQ + correct key), per-question points | Implemented (DRAFT-only authoring) |
| R6 | Attempt timer starts on first question fetch; single submission; auto-grade | Implemented |
| R7 | Points attribution to per-mentorship `TotalPoints` | Implemented (pessimistic-lock deltas) |
| R8 | `passPoints` threshold / next-item unlock logic | **Not implemented** (field stored, never compared; no unlock code anywhere) |
| R9 | Hard deadline closure / auto-close scheduled jobs | **Not implemented** (soft late penalty only; `@Scheduled` import unused) |
| R10 | Peer/cross-student submission visibility | Not applicable (and partially leaking — see issues) |

## 3. Current Implementation — entities & enums

- `entity/tasks/Task`: title, description(≤10k), `points`, `passPoints`(dead), `estimatedMinutes`, `status`(`DRAFT/PUBLISHED/CLOSED`), `dueAt`, `attachmentUrl`/`uploadedAttachmentPath`, `@OneToMany submissions`; week FK.
- `entity/tasks/TaskSubmission`: task FK + student FK (unique task_id,student_id), `fileUrl`/`uploadedFilePath`, `rawScore`(**stores task.points — mislabeled**), `finalScore`, `feedBack`, `status`(`SUBMITTED→GRADED`; `CLOSED/IN_PROGRESS` unused), `submittedAt`, `gradedAt`, `isLate`, `pointsApplied` (regrade bookkeeping).
- `entity/projects/Project`: title(≤150), `brief`(≤10k), `goal`, `descriptionUrl`, attachment, `startAt`/`endAt`, `points`, `status`(`DRAFT/PUBLISHED/CLOSED`); week FK.
- `entity/projects/ProjectSubmission`: same shape as TaskSubmission keyed to project; unique (project_id, student_id); reuses `SubmissionStatus`.
- `entity/quiz/Quiz`: week FK, title(≤100), description(≤500), `durationMinutes`, `status`(`DRAFT/PUBLISHED/CLOSED`); **no points/deadline on quiz**.
- `entity/quiz/Question`: quiz FK, text(≤500), per-question `points` (int), `correctAnswer` enum `AnswerChoices{A,B,C,D}`, options A–D; `@OneToMany studentAnswers`.
- `entity/quiz/QuizSubmission`: unique (student_id, quiz_id); `score` Double, `startDate`, `endDate` (attempt window); `@OneToMany answers`.
- `entity/quiz/StudentAnswer`: submission FK, question FK, `selectedAnswer` String; unique (submission_id, question_id).
- Points storage: `entity/points/TotalPoints` (per student+mentorship; unique; `totalPoints`).

## 4. Workflows → detail docs

- Tasks (create → submit → grade → points, statistics bug): [tasks-workflow.md](tasks-workflow.md)
- Projects (deadline model, grading): [projects-workflow.md](projects-workflow.md)
- Quizzes (authoring → attempt → auto-grade → key disclosure): [quizzes-workflow.md](quizzes-workflow.md)
- Aggregations (My-Learning, achievements) + points pipeline: [my-learning-and-achievements.md](my-learning-and-achievements.md)

## 5. Files

Submission + attachment files flow through `service/fileSotageService/FileStorageService` (Tika MIME validation, sanitization, atomic write):
- `uploads/submissions/task-…` (task submissions), `uploads/submissions/project-…`, `uploads/task-attachment/…`, `uploads/project-attachment/…`
- External `fileUrl` **submission parameter is unvalidated** (validated DTOs exist but are unused by controllers — files are the primary path; external URL stale-URL risk).
- Serving: `/uploads/**` static+public, `/files/**` typed accessors enforce ownership; generic `GET /files?filePath=` does not — detail and risk: [../file-storage/MDS.md](../file-storage/MDS.md).

## 6. Known Issues (summary; full detail in ../ISSUES.md)

- **LEARN-01 (Critical):** quiz answer-key disclosure endpoint `/api/v1/my-answers/{quizId}` returns every `correctAnswer` pre-submission → trivial perfect score (defeats auto-grading).
- **LEARN-02 (High):** unguarded reads of any task/project by id and by mentorship (`getById`, `/filter/{msid}`) incl. DRAFT content and internal `uploadedFilePath`.
- **LEARN-03 (High):** generic file download endpoint bypasses ownership (`GET /files?filePath=`).
- **LEARN-04 (Medium):** statistic bug — task `pendingReview = totalStudents − totalPages` (`TaskServiceImpl#getTaskStatistics` L272; project twin computes it correctly).
- **LEARN-05 (Medium):** late-submission rule asymmetric — late *first* submissions accepted (graded at full points; only late *resubmit* blocked).
- **LEARN-06 (Medium):** `points` editable post-grading without recalculation; content deletion leaves orphan points; regrade delta correct — regrade-after-resubmit leaves points visible while status flips back to SUBMITTED.
- **LEARN-07 (Medium):** "opened quiz" rows count as submitted in My-Learning/progress and as 0-score in averages.
- **LEARN-08 (Medium):** `passPoints` dead; no unlock logic despite documentation claims.
- **LEARN-09 (Low):** `rawScore` = max score not raw; quiz `(int)` truncation of fractional scores; late penalty = 1 point/minute floored.

## 7. Open Questions

- Was a pass-threshold/unlock chain ("score ≥ passPoints unlocks next lecture") planned? *(No code exists — README/books mention checkpoint semantics.)* Decide whether to implement or delete the `passPoints` field.
- Should late submissions be rejected outright (product decision) instead of accepted+flagged?
- Should answers become visible only after grading/submission? (Current review endpoint makes key public pre-submission.)

## 8. Related Documentation

[tasks-workflow.md](tasks-workflow.md) · [projects-workflow.md](projects-workflow.md) · [quizzes-workflow.md](quizzes-workflow.md) · [my-learning-and-achievements.md](my-learning-and-achievements.md) · [../file-storage/MDS.md](../file-storage/MDS.md) · [../gamification/MDS.md](../gamification/MDS.md) · [../ISSUES.md](../ISSUES.md)
