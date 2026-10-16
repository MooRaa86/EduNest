# My-Learning, Achievements & the Points Pipeline

How student progress is aggregated and how every graded event feeds `TotalPoints`.

## 1. Points award sites (complete list, verified callers)

| Trigger | Source | Delta |
|---|---|---|
| Mentor grades a **task submission** | `TaskSubmissionServiceImpl#grade` L214 | `finalScore − pointsApplied` (regrade-safe) |
| Mentor grades a **project submission** | `ProjectSubmissionServiceImpl#gradeProject` L178 | same delta logic |
| Student submits a **quiz** | `QuizSubmissionServiceImpl#submitQuizAnswers` L145 | `(int) finalScore` *(truncate)* |
| Mentor awards a **badge** | `BadgeAwardService#awardBadge` L83 | `badge.points` |
| Mentor ends a **live session** with attendance | `LiveSessionServiceImp#calculateAttendancePoints` L470 | **5 fixed points if Present** |
| Mentor edits a badge's points | `BadgeService#updateBadge` L102–105 → bulk `updatePointsForBadgeAwards` | delta applied retro to all awardees *(no clamp)* |

Concurrency: `TotalPointsServiceImp#applyDelta` uses `TotalPointsRepository#findForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) and **clamps at 0** — race-safe per-mentorship total. The badge bulk-update path bypasses the lock+clamp (inconsistency, GAME-05).

## 2. Leaderboards

Sources (`repository/points/TotalPointsRepository`):
- `findTopStudentsByMentorship` — top-by-points.
- `findStudentsWithRanksByMentorshipId` — SQL `ROW_NUMBER() OVER (ORDER BY totalPoints DESC)`.
- `findLeaderboardByMentorshipId` — JPQL with correlated max-`BadgeAward.createdAt` tie-break join (used **unpaginated** by `CertificateService`).
- `findStudentInLeaderboard(mentorshipId, email)` — rank via `COUNT(tp2)+1` with `s2.id < s.id` tie-break.

Consumers: mentorship dashboard (ranks/top learners), `MentorshipLeaderboardService` (**enrollment guard commented out** — MSH-03), `CertificateService` (rank source).

## 3. My-Learning aggregation

`MyLearningController` (STUDENT) → `MyLearningService#getMyLearning`:
- Native query `EnrollmentRepository#findAllEnrolledMentorshipsWithProgress` (ACTIVE/COMPLETED): totals of PUBLISHED tasks/quizzes/projects + student's submission presence + `totalPoints` per mentorship.
- Progress formula: `(submittedTasks+submittedQuizzes+submittedProjects) * 100 / (totalTasks+totalQuizzes+totalProjects)`.
- **Flaws:**
  - KPI header (`completedMentorships`, `averageProgress`, `totalPoints`) computed **from the current page only** (L50–53).
  - Quiz submissions counted as submitted when merely *opened* (row created on first question fetch) → progress inflation (LEARN-07); averages similarly count opened rows as 0-score (`QuizServiceImpl#calculateAverageScore`).

## 4. Student achievements surface

`StudentAchievementController` (STUDENT) → `StudentAchievementService`:
- Paged badge awards (newest first) with badge points + awarder name + date.
- Paged graded project submissions (title, mentorship, mentor, feedback, files) — the portfolio feed.

## 5. Data-integrity gaps (cross-links)

- Deleting tasks/projects/quizzes does **not** revoke points (LEARN-06b).
- Mentor can edit `points` after grading without recalculation (LEARN-06a).
- Badge point edits hit rows in bulk without clamp/lock (GAME-05).
- Attendance points are awarded regardless of whether any snapshot exists (everyone defaults 0/Absent) — semantics defined in [../live-sessions/MDS.md](../live-sessions/MDS.md).

## 6. Test recommendations

1. Idempotent re-grading: grade→re-grade→(resubmit→re-grade) produces exact expected `TotalPoints`.
2. Quiz truncation: fractional finalScore handling (currently lost points) — document decision.
3. Progress math: exclude *opened-not-submitted* quizzes (post-fix), page-size independence for KPIs (post-fix).
4. Bulk badge-point update clamps at 0 (post-fix).
