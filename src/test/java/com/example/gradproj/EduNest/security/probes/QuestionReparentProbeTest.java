package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G15: question re-parenting across quiz ownership.
 * Flips applied by EB-26 / P1-19 (2026-11-01) — see the per-probe notes.
 */
@Tag("critical")
class QuestionReparentProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private QuizRepository quizzes;
	@Autowired private QuestionRepository questions;

	/**
	 * G15 — question re-parent guard (FLIPPED 2026-11-01, EB-26 / P1-19).
	 * V-06 verdict (P0-08): CONFIRMED — this exact scenario returned 200
	 * pre-fix and the question landed in the foreign quiz (code audit:
	 * QuestionServiceImp#updateQuestion gated only the SOURCE question's
	 * ownership and the target quiz's DRAFT status; the TARGET quiz's
	 * ownership was never validated). Recorded as the two-mentor POC.
	 *
	 * Now (P1-19): updateQuestion enforces
	 * {@code isMentorOwnQuiz(dto.quizId, email)} BEFORE the status check, so
	 * cross-mentor re-parents are rejected with 403 — and a foreign quiz's
	 * DRAFT status is not leaked either.
	 */
	@Test
	@DisplayName("G15/confined: mentor moves his OWN question into a FOREIGN mentor's quiz → 403")
	void g15_mentorReparentsQuestionIntoForeignQuiz() throws Exception {
		// mentor A + quiz A (owns it); mentor B + quiz B DRAFT (the target)
		var chainA = auth.mentorship("probe-g15-mentor-a@example.org", Status.ACTIVE);
		Week weekA = weeks.saveAndFlush(Week.builder().mentorship(chainA.mentorship()).title("g15 week a").build());
		Quiz quizA = quizzes.saveAndFlush(Quiz.builder().week(weekA).title("quiz A")
				.description("g15 quiz").durationMinutes(10)
				.status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.DRAFT).build());
		var question = questions.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.Question.builder()
				.quiz(quizA).text("g15 question").points(1)
				.correctAnswer(com.example.gradproj.EduNest.enums.quiz.AnswerChoices.A)
				.optionA("a").optionB("b").optionC("c").optionD("d").build());

		var chainB = auth.mentorship("probe-g15-mentor-b@example.org", Status.ACTIVE);
		Week weekB = weeks.saveAndFlush(Week.builder().mentorship(chainB.mentorship()).title("g15 week b").build());
		Quiz quizB = quizzes.saveAndFlush(Quiz.builder().week(weekB).title("quiz B foreign")
				.description("g15 quiz b").durationMinutes(10)
				.status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.DRAFT).build());

		// mentor A calls the update; quizId points to mentor B's quiz → 403
		mockMvc.perform(patch("/api/v1/question/{id}", question.getId())
						.header("Authorization", auth.authToken(chainA.mentor().getEmail(), AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"quizId\":" + quizB.getId() + ",\"text\":\"g15 question\"," +
								"\"points\":1,\"correctAnswer\":\"A\",\"optionA\":\"a\"," +
								"\"optionB\":\"b\",\"optionC\":\"c\",\"optionD\":\"d\"}"))
				.andExpect(status().isForbidden());

		// no re-parent happened
		org.junit.jupiter.api.Assertions.assertEquals(
				quizA.getId(),
				questions.findById(question.getId()).orElseThrow().getQuiz().getId(),
				"G15: the question must stay in its source quiz");
	}

	/**
	 * G15b — the legitimate path unchanged: a mentor can still move his OWN
	 * question between his OWN quizzes (both DRAFT).
	 */
	@Test
	@DisplayName("G15b: mentor re-parents his own question between his own quizzes → 200")
	void g15b_selfReparentStillWorks() throws Exception {
		var chainA = auth.mentorship("probe-g15-mentor-a@example.org", Status.ACTIVE);
		Week weekA = weeks.saveAndFlush(Week.builder().mentorship(chainA.mentorship()).title("g15b week").build());
		Quiz quizSrc = quizzes.saveAndFlush(Quiz.builder().week(weekA).title("g15b quiz src")
				.description("g15b").durationMinutes(10)
				.status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.DRAFT).build());
		var question = questions.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.Question.builder()
				.quiz(quizSrc).text("g15b question").points(1)
				.correctAnswer(com.example.gradproj.EduNest.enums.quiz.AnswerChoices.A)
				.optionA("a").optionB("b").optionC("c").optionD("d").build());
		Quiz quizDst = quizzes.saveAndFlush(Quiz.builder().week(weekA).title("g15b quiz dst")
				.description("g15b").durationMinutes(10)
				.status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.DRAFT).build());

		mockMvc.perform(patch("/api/v1/question/{id}", question.getId())
						.header("Authorization", auth.authToken(chainA.mentor().getEmail(), AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"quizId\":" + quizDst.getId() + ",\"text\":\"g15b question\"," +
								"\"points\":1,\"correctAnswer\":\"A\",\"optionA\":\"a\"," +
								"\"optionB\":\"b\",\"optionC\":\"c\",\"optionD\":\"d\"}"))
				.andExpect(status().isOk());

		org.junit.jupiter.api.Assertions.assertEquals(
				quizDst.getId(),
				questions.findById(question.getId()).orElseThrow().getQuiz().getId(),
				"G15b: self re-parent must keep working");
	}
}
