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
4. **Late rule** (L110–117): `now > endDate` → `finalScore = max(0, score − minutesLate)` — soft −1 point/minute **penalty only; submission never rejected; no hard close**. The unused `@Scheduled` import promises none-existent auto-close; closing is manual via mentor status change.
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

- No global pass/unlock, no per-quiz deadline field (only attempt window), no multi-attempt support (unique constraint + single-submission guard).
- Quiz status `CLOSED` blocks student GET (quiz fetch endpoint hides DRAFT from students pre-fetch? — `getQuizDetails` checks mentor-or-enrolled and hides DRAFT from students).

## 7. Test recommendations

1. **Key-leak regression test**: `/my-answers` before submit must not include `correctAnswer` (post-fix) — **delivered** 2026-11-01: `QuizAnswerKeyScopingProbesTest` (P1-18/EB-25).
2. Attempt window: submit 1 second before endDate ok; after → penalty math exact; re-submit rejected.
3. Scoring: partial correctness, wrong answer, empty answers list accepted as 0 (current behavior), unknown question rejected.
4. First-open creates attempt row; progress accounting excludes unsubmitted rows (post-fix).
