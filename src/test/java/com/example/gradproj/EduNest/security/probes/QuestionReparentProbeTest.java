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
 */
@Tag("critical")
class QuestionReparentProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private QuizRepository quizzes;
	@Autowired private QuestionRepository questions;

	/**
	 * G15 — question re-parenting across quiz ownership (probe-adjudicated shape).
	 * ⚠️ REGISTER CORRECTION: QuestionServiceImp#updateQuestion gates on
	 * `isMentorOwnQuestion` = ownership of the question's CURRENT quiz
	 * (q.quiz.week.mentorship.mentor.email) — it NEVER validates the TARGET quiz
	 * (dto.quizId). So a mentor CAN move HIS OWN question into ANOTHER mentor's
	 * quiz (content-mixing across mentorship boundaries; the foreign quiz then
	 * serves the foreigner's question when published).
	 * EXPECT TODAY: 200 and the question re-pointed into the foreign quiz.
	 * FLIPS WITH: [P1-19] re-parent guard (per ISSUE-MAPPING) → expect 403 then.
	 */
	@Test
	@DisplayName("G15/todayOpen: mentor moves his OWN question into a FOREIGN quiz → 200")
	void g15_mentorReparentsQuestionIntoForeignQuiz() throws Exception {
		// mentor A + quiz A (owns it); mentor B + quiz B DRAFT (the unvalidated target)
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

		// mentor A calls the update; quizId points to mentor B's quiz → succeeds today
		var result = mockMvc.perform(patch("/api/v1/question/{id}", question.getId())
						.header("Authorization", auth.authToken(chainA.mentor().getEmail(), AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"quizId\":" + quizB.getId() + ",\"text\":\"g15 question\","
								+ "\"points\":1,\"correctAnswer\":\"A\",\"optionA\":\"a\","
								+ "\"optionB\":\"b\",\"optionC\":\"c\",\"optionD\":\"d\"}"))
				.andReturn();

		org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus(),
				"G15 audit: expect foreign-quiz re-parent to succeed. Body: "
						+ result.getResponse().getContentAsString());

		// hole documented: the TARGET quiz ownership was never validated
		org.junit.jupiter.api.Assertions.assertEquals(
				quizB.getId(),
				questions.findById(question.getId()).orElseThrow().getQuiz().getId(),
				"G15 audit: question was allowed to move into another mentor's quiz");

		// hole documented: ownership NOT validated
		org.junit.jupiter.api.Assertions.assertEquals(
				quizB.getId(),
				questions.findById(question.getId()).orElseThrow().getQuiz().getId(),
				"G15 audit: question was allowed to move into another mentor's quiz");
	}
}
