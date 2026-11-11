# Quizzes Workflow (authoring → attempt → auto-grade)

Verified from `service/quiz/quiz/QuizServiceImpl`, `service/quiz/question/QuestionServiceImp`, `service/quiz/submission/QuizSubmissionServiceImpl`.

## 1. Authoring (mentor)

- `QuizServiceImpl#createQuiz` (L47–86): week + ownership enforced; status default DRAFT; publish notifies students.
- Questions (`QuestionServiceImp#createQuestion` L40–66): **only while quiz DRAFT**; MCQ A–D (`AnswerChoices`), per-question `points` (int), `correctAnswer` stored as enum on the question (plaintext in DB — key discovery is the main leak below).
- Edit quirks: `updateQuestion` since P1-19 / EB-26 validates BOTH the source question's ownership AND the **target quiz's** ownership (`isMentorOwnQuiz`, enforced before the DRAFT-status check so a foreign quiz's status is not probed) — cross-mentor re-parent → 403, mentor's own quiz-to-quiz moves unchanged; authoring gate is only on create. (V-06/P0-08 verdict: the cross-mentor gap was CONFIRMED pre-fix; probes `QuestionReparentProbeTest` G15/G15b.)

## 2. Start an attempt (side effect on GET)

`QuestionServiceImp#getQuestionsByQuizIdForStudent` (L82–115):
1. Requires enrollment + quiz `PUBLISHED`.
2. **First GET creates the `QuizSubmission` row** with `startDate = now`, `endDate = now + durationMinutes`.
   - Consequence: the attempt timer starts on first open (documented behavior), and an *unsubmitted* attempt row exists thereafter — which inflates progress/averages elsewhere (LEARN-07).
3. Returns questions/options; crucially **not** correct answers at this endpoint (they leak via a different one — §4).

## 3. Submit & auto-grade

`QuizSubmissionServiceImpl#submitQuizAnswers` (L67–173):
1. Requires enrollment + PUBLISHED + an existing attempt row ("Quiz not started yet").
2. **Single attempt**: existing non-null `score` → "Quiz already submitted" (DB unique (student, quiz) as backstop).
3. Scoring `calculateScore` (L305–321): `selectedAnswer.equals(String.valueOf(question.correctAnswer))` → accumulate question points; duplicate answers prevented by clearing + verifying each question id belongs to the quiz.
4. **Late rule (updated EB-53 / P4-04, 2026-11-11)**: a submission whose `endDate` is already past is accepted **only within the grace window** (`app.submission.late-grace-minutes`, default 10): `minutesLate = MINUTES.between(endDate, now)`; if `minutesLate > grace` → `globalLogicEx("Quiz submission window has closed (deadline was …)")` — thrown **before any mutation** (score, answers, TotalPoints untouched). Inside grace the −1 pt/min penalty applies, clamped at 0: `finalScore = max(0, raw − minutesLate)`, and the row is flagged `isLate=true` (persisted column `quiz_submission.is_late`, exposed on the student response and mentor listing rows). On-time submissions get `isLate=false` and full score.
5. Points: `applyDelta(student, mentorship, (int) finalScore)` — **truncates** fractional scores (currently whole since question points are integers, but fragile).
6. Pass criterion (display-time): `score ≥ fullMark/2` → "Passed" else "Failed" (`getAllSubmissionsByQuiz` L283–291; `getQuizOverviewDto` L345–354).
7. Pushes notifications mentor-side ("New Quiz Submission") and student-side ("Quiz Submitted").

## 4. Answer-key disclosure (critical — FIXED 2026-11-01, P1-18/EB-25)

`GET /api/v1/my-answers/{quizId}` (`QuizSubmissionController#getStudentQuizReview` L58) → `QuizSubmissionServiceImpl#getStudentQuizReview` (L196–257):
- Pre-fix: returned **`correctAnswer` for every question**, guarded only by enrollment (LEARN-01, Critical) — callable before submitting → perfect score trivially.
- **Fixed (P1-18/EB-25, 2026-11-01):** the key is populated **only once the attempt is genuinely submitted** — `QuizSubmission#score != null`, the same marker `submitQuizAnswers` uses as its "already submitted" guard (opening a quiz creates the row with `score == null`, LEARN-07). Pre-submission the field stays null and `StudentQuizReviewDTO` **omits it from the JSON entirely** (`@JsonInclude(NON_NULL)` on the field, not class-level — other fields keep their null contract).
- Mentor-side `GET /answer/{studentId}/{quizId}` keeps full keys and asserts quiz ownership (`SecurityService#isMentorOwnQuiz` → `AccessDeniedException` → 403 for foreign mentors).
- Regression probes: `QuizAnswerKeyScopingProbesTest` (key omitted pre-submission / key visible after genuine submit / mentor view 403-foreign vs 200-owner).

## 5. Mentor-side review surfaces

- `GET /submissions/quiz/{quizId}` (mentor, own-quiz) — per-student rows incl. "Not Submitted" label for opened-but-unsubmitted attempts; NPE edge if both score & startDate were null (data-dependent; today's rows always have startDate).
- `GET /submissions/student/{studentId}` — mentor access via `validateMentorHasAccessToStudent`.
- `GET /answer/{studentId}/{quizId}` — inspect a student's chosen answers; ownership-asserted (`SecurityService#isMentorOwnQuiz` → 403 for foreign mentors; pinned by `QuizAnswerKeyScopingProbesTest`, P1-18/EB-25).

## 6. Data model notes

- No global pass/unlock, no multi-attempt support (unique constraint + single-submission guard).
- **Close policy (EB-53 / P4-04, D-08 — decided 2026-11-11):**
  - `quiz.closeAt` is an **explicit, nullable** field (new V12 column `quiz.close_at`). It is never derived from attempt `endDate`s. Legacy PUBLISHED quizzes with `closeAt == null` are never auto-closed and never backfilled; they keep pre-EB-53 behavior (new attempts always allowed).
  - Publishing (create-as-PUBLISHED, updateQuiz DRAFT→PUBLISHED, changeStatus → PUBLISHED) **requires a future `closeAt`** — null → `"closeAt is required when publishing a quiz"`, past → `"closeAt must be in the future when publishing a quiz"`. `closeAt` is echoed on `QuizResponseDTO` from all detail/list builders.
  - **New attempts blocked after `closeAt`**: `QuestionServiceImp#getQuestionsByQuizIdForStudent` throws `"Quiz is closed for new attempts"` when `now >= closeAt` and the caller has no attempt row yet. In-progress attempts are unaffected (they keep their own `endDate` + grace window and may still submit).
  - **Scheduler auto-close**: `QuizCloseSweepService` (`app.quiz.auto-close.enabled`, sweep every `app.quiz.auto-close.sweep-rate-ms`, default 300 s; scheduled only when `app.scheduling.enabled`) runs `PUBLISHED → CLOSED` when `closeAt` has passed **and no attempt is eligible to submit** (no `endDate + grace > now`). Non-TX dispatcher + `@Transactional` worker locking week → quiz (canonical order, D-15b Option B pattern); idempotent. Mentor manual `CLOSED` (§1 status change) still works and the sweep never touches already-closed quizzes.
- Quiz status `CLOSED` blocks student GET (quiz fetch endpoint hides DRAFT from students pre-fetch? — `getQuizDetails` checks mentor-or-enrolled and hides DRAFT from students).

## 7. Test recommendations

1. **Key-leak regression test**: `/my-answers` before submit must not include `correctAnswer` (post-fix) — **delivered** 2026-11-01: `QuizAnswerKeyScopingProbesTest` (P1-18/EB-25).
2. Attempt window: submit 1 second before endDate ok; after → penalty math exact; re-submit rejected.
3. Scoring: partial correctness, wrong answer, empty answers list accepted as 0 (current behavior), unknown question rejected.
4. First-open creates attempt row; progress accounting excludes unsubmitted rows (post-fix).
5. **EB-53 probes delivered 2026-11-11 (`QuizLatePolicyPostgresTest`)**: late-within-grace penalty math (score, isLate, TotalPoints), beyond-grace rejection with zero state mutation (no score, no answers, no points, isLate stays null), new-attempts blocked after closeAt (no attempt row created; in-progress attempt still submittable), sweep close/idempotency/skips-while-eligible, legacy null-closeAt never auto-closes, publish requires future closeAt, mentor manual close unaffected, post-close question edits rejected (pre-existing DRAFT gate).
