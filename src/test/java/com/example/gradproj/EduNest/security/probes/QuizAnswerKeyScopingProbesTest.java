package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-18 / EB-25 — quiz answer-key scoping probes (LEARN-01).
 *
 * Audit ({@code mds/ISSUES.md} LEARN-01): {@code GET /api/v1/my-answers/{quizId}}
 * returned {@code correctAnswer} for EVERY question as soon as a submission row
 * existed — and opening a quiz already creates that row (LEARN-07), so a student
 * could open the quiz, read the key off the review payload and submit 100%
 * without ever solving anything (defeats auto-grading).
 *
 * Fix ({@code QuizSubmissionServiceImpl#getStudentQuizReview}): the key is
 * populated only when the attempt is genuinely submitted —
 * {@code QuizSubmission#score != null}, the same marker {@code submitQuizAnswers}
 * already uses as its "already submitted" guard. Pre-submission the field stays
 * null and {@code StudentQuizReviewDTO} omits it from the JSON entirely
 * ({@code @JsonInclude(NON_NULL)} on the field).
 *
 * Mentor answer view {@code GET /api/v1/answer/{studentId}/{quizId}} keeps the
 * full keys but must assert quiz ownership — the explicit service-level check
 * ({@code SecurityService#isMentorOwnQuiz} → AccessDeniedException → 403, the
 * error shape proven by the EB-24 probes) is pinned here.
 */
@Tag("critical")
class QuizAnswerKeyScopingProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private QuizRepository quizzes;
	@Autowired private QuestionRepository questions;
	@Autowired private QuizSubmissionRepository submissions;

	/** (quizId, questionId, studentId, studentEmail) the probes drive through the live chain. */
	private record Seed(Long quizId, Long questionId, Long studentId, String studentEmail) {
	}

	/**
	 * Seeds the production-shaped chain via the same direct-repo pattern the
	 * G15 probes use: mentor-owned ACTIVE mentorship → week → PUBLISHED quiz →
	 * one question with key A; the probe student is enrolled. Every test uses
	 * its own tag-unique emails, so the (student, quiz) pairs never collide in
	 * the shared H2 schema.
	 */
	private Seed seed(String tag) {
		String mentorEmail = "probe-l01-" + tag + "-mentor@example.org";
		String studentEmail = "probe-l01-" + tag + "-student@example.org";
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship())
				.title("l01 week " + tag).build());
		Quiz quiz = quizzes.saveAndFlush(Quiz.builder().week(week).title("l01 quiz " + tag)
				.description("P1-18 probe").durationMinutes(10)
				.status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.PUBLISHED).build());
		var question = questions.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.Question.builder()
				.quiz(quiz).text("P1-18 question " + tag).points(1)
				.correctAnswer(com.example.gradproj.EduNest.enums.quiz.AnswerChoices.A)
				.optionA("a").optionB("b").optionC("c").optionD("d").build());
		Long studentId = auth.enroll(chain.mentorship().getId(), studentEmail).getStudent().getId();
		return new Seed(quiz.getId(), question.getId(), studentId, studentEmail);
	}

	/** Student token — raw JWT, no Bearer prefix (AuthFixture contract note). */
	private String studentToken(Seed s) {
		return auth.authToken(s.studentEmail(), AuthFixture.STUDENT);
	}

	/** The student opens the quiz: creates the attempt row with score == null (LEARN-07). */
	private void openQuiz(Seed s) throws Exception {
		mockMvc.perform(get("/api/v1/question/student/fetch/{quizId}", s.quizId())
						.header("Authorization", studentToken(s)))
				.andExpect(status().isOk());
	}

	private String myAnswers(Seed s) throws Exception {
		return mockMvc.perform(get("/api/v1/my-answers/{quizId}", s.quizId())
						.header("Authorization", studentToken(s)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

	/**
	 * LEARN-01 — open-but-not-submitted: the student opens the quiz (attempt
	 * row exists, score still null) and then reviews. 200, questions/options/
	 * selectedAnswer are all there, but the JSON must NOT contain
	 * correctAnswer — the key is absent from the payload, not merely null.
	 */
	@Test
	@DisplayName("LEARN-01: opened, not submitted → review 200 WITHOUT the key in JSON")
	void learn01_keyOmittedBeforeSubmission() throws Exception {
		Seed s = seed("opened");

		openQuiz(s);
		assertThat(submissions.findByStudent_IdAndQuiz_Id(s.studentId(), s.quizId())
				.orElseThrow().getScore()).as("opened, never submitted").isNull();

		String body = myAnswers(s);

		assertThat(body).doesNotContain("\"correctAnswer\"");
		assertThat(body).contains("\"questionId\"");
		assertThat(body).contains("\"optionA\":\"a\"");
	}

	/**
	 * LEARN-01 — the legitimate path unchanged: once the student genuinely
	 * submits (score set — the same marker the submit endpoint itself uses as
	 * its "already submitted" guard), the review 200 shows my answer AND the
	 * key, so FE post-submission review keeps working.
	 */
	@Test
	@DisplayName("LEARN-01: genuinely submitted → review 200 WITH the key")
	void learn01_keyVisibleAfterSubmission() throws Exception {
		Seed s = seed("submitted");

		openQuiz(s);
		mockMvc.perform(post("/api/v1/submit-quiz-answer/{quizId}", s.quizId())
						.header("Authorization", studentToken(s))
						.contentType("application/json")
						.content("{\"answers\":[{\"questionId\":" + s.questionId()
								+ ",\"selectedAnswer\":\"A\"}]}"))
				.andExpect(status().isCreated());
		assertThat(submissions.findByStudent_IdAndQuiz_Id(s.studentId(), s.quizId())
				.orElseThrow().getScore()).as("genuinely submitted").isNotNull();

		String body = myAnswers(s);

		assertThat(body).contains("\"correctAnswer\":\"A\"");
		assertThat(body).contains("\"selectedAnswer\":\"A\"");
	}

	/**
	 * P1-18 — mentor answer view keeps full keys but is ownership-scoped:
	 * the quiz-OWNING mentor gets the student's answers (200); a FOREIGN
	 * mentor asking for the same student's answers is rejected 403 by the
	 * explicit {@code SecurityService#isMentorOwnQuiz} check in
	 * {@code getStudentAnswers}.
	 */
	@Test
	@DisplayName("P1-18: mentor /answer/{student}/{quiz} — foreign mentor 403, owner 200")
	void p118_mentorAnswerViewOwnershipScoped() throws Exception {
		Seed s = seed("answer-view");
		openQuiz(s); // submission row must exist, so the owner read reaches the answers

		// foreign mentor (owns a different mentorship, never linked to this quiz)
		auth.mentorship("probe-l01-answer-view-foreign@example.org", Status.ACTIVE);
		mockMvc.perform(get("/api/v1/answer/{studentId}/{quizId}", s.studentId(), s.quizId())
						.header("Authorization", auth.authToken(
								"probe-l01-answer-view-foreign@example.org", AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());

		// the quiz owner still reads the student's answers
		mockMvc.perform(get("/api/v1/answer/{studentId}/{quizId}", s.studentId(), s.quizId())
						.header("Authorization", auth.authToken(
								"probe-l01-answer-view-mentor@example.org", AuthFixture.MENTOR)))
				.andExpect(status().isOk());
	}
}
