package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Quiz Lifecycle Flow Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: Student opens quiz (attempt row created with score=null) ->
 *    submits answers -> score computed & points applied -> review contains answer keys & score ->
 *    student notified.
 * 2. Failure-path: Student cannot submit a quiz that was already submitted (400 Bad Request).
 * 3. Failure-path: Unenrolled student cannot open or submit quiz (403 Forbidden).
 */
@Tag("critical")
@Tag("business-flow")
public class QuizLifecycleFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private QuizRepository quizzes;
	@Autowired private QuestionRepository questions;
	@Autowired private QuizSubmissionRepository submissions;
	@Autowired private TotalPointsRepository totalPoints;
	@Autowired private CommissionRepository commissions;
	@Autowired private UserNotificationRepository userNotifications;

	@BeforeEach
	void ensureCommission() {
		if (commissions.count() == 0) {
			commissions.saveAndFlush(Commission.builder().rate(10.0).build());
		}
	}

	@Test
	@DisplayName("Flow 3 Happy Path: Open quiz -> Submit answers -> Score & Points applied -> Review available")
	void quizLifecycleFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m3-mentor-" + unique + "@example.org";
		String studentEmail = "flow-m3-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();

		// Enroll student & initialize TotalPoints at 0
		auth.enroll(mentorshipId, studentEmail);
		totalPoints.saveAndFlush(TotalPoints.builder()
				.student(student)
				.mentorship(chain.mentorship())
				.totalPoints(0)
				.build());

		// Seed Week, PUBLISHED Quiz, and Questions
		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Quiz quiz = quizzes.saveAndFlush(Quiz.builder()
				.week(week)
				.title("Java Fundamentals Quiz " + unique)
				.description("Core Java and OOP concepts")
				.durationMinutes(30)
				.status(QuizStatus.PUBLISHED)
				.build());

		Question q1 = questions.saveAndFlush(Question.builder()
				.quiz(quiz)
				.text("What is JVM?")
				.points(10)
				.correctAnswer(AnswerChoices.A)
				.optionA("Java Virtual Machine")
				.optionB("Java Variable Method")
				.optionC("Java Verified Model")
				.optionD("None")
				.build());

		Question q2 = questions.saveAndFlush(Question.builder()
				.quiz(quiz)
				.text("Is Java object-oriented?")
				.points(10)
				.correctAnswer(AnswerChoices.B)
				.optionA("No")
				.optionB("Yes")
				.optionC("Sometimes")
				.optionD("Never")
				.build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		// Step 1: Student opens quiz -> creates attempt row with score = null
		mockMvc.perform(get("/api/v1/question/student/fetch/{quizId}", quiz.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.['Quiz Questions']").isArray());

		QuizSubmission attempt = submissions.findByStudent_IdAndQuiz_Id(student.getId(), quiz.getId())
				.orElseThrow();
		assertThat(attempt.getScore()).isNull();
		assertThat(attempt.getStartDate()).isNotNull();

		// Step 2: Student submits correct answers (A for q1, B for q2 -> full 20 points)
		String submissionJson = "{"
				+ "\"answers\":["
				+ "{\"questionId\":" + q1.getId() + ",\"selectedAnswer\":\"A\"},"
				+ "{\"questionId\":" + q2.getId() + ",\"selectedAnswer\":\"B\"}"
				+ "]}";

		mockMvc.perform(post("/api/v1/submit-quiz-answer/{quizId}", quiz.getId())
						.header("Authorization", studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(submissionJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.apiResponse.message").value("Quiz submitted successfully"));

		// Step 3: Assert observable effect on QuizSubmission: score = 20.0
		QuizSubmission completed = submissions.findByStudent_IdAndQuiz_Id(student.getId(), quiz.getId())
				.orElseThrow();
		assertThat(completed.getScore()).isEqualTo(20.0);

		// Step 4: Assert observable effect on TotalPoints: +20 points applied
		TotalPoints tp = totalPoints.findByStudent_IdAndMentorship_Id(student.getId(), mentorshipId)
				.orElseThrow();
		assertThat(tp.getTotalPoints()).isEqualTo(20);

		// Step 5: Assert student can review answers with keys visible
		mockMvc.perform(get("/api/v1/my-answers/{quizId}", quiz.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.Review").isArray());

		// Step 6: Assert student received quiz graded notification
		Page<UserNotification> studentNotifs = userNotifications.findWithNotification(studentEmail, PageRequest.of(0, 10));
		assertThat(studentNotifs.getContent()).anyMatch(un ->
				un.getNotification().getTitle().equals("Quiz Submitted") &&
				un.getNotification().getContent().contains("You scored 20")
		);
	}

	@Test
	@DisplayName("Flow 3 Failure Path: Already submitted quiz cannot be submitted again (400 Bad Request)")
	void quizLifecycleFlow_cannotSubmitTwice() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m3-twice-m-" + unique + "@example.org";
		String studentEmail = "flow-m3-twice-s-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		auth.enroll(chain.mentorship().getId(), studentEmail);

		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Quiz quiz = quizzes.saveAndFlush(Quiz.builder()
				.week(week)
				.title("Quiz " + unique)
				.description("Single attempt quiz")
				.durationMinutes(15)
				.status(QuizStatus.PUBLISHED)
				.build());

		Question q = questions.saveAndFlush(Question.builder()
				.quiz(quiz).text("Sample Question").points(5).correctAnswer(AnswerChoices.A)
				.optionA("A").optionB("B").optionC("C").optionD("D").build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		// Open quiz
		mockMvc.perform(get("/api/v1/question/student/fetch/{quizId}", quiz.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isOk());

		String submissionJson = "{\"answers\":[{\"questionId\":" + q.getId() + ",\"selectedAnswer\":\"A\"}]}";

		// First submit succeeds
		mockMvc.perform(post("/api/v1/submit-quiz-answer/{quizId}", quiz.getId())
						.header("Authorization", studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(submissionJson))
				.andExpect(status().isCreated());

		// Second submit must fail
		mockMvc.perform(post("/api/v1/submit-quiz-answer/{quizId}", quiz.getId())
						.header("Authorization", studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(submissionJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("Quiz already submitted"));
	}

	@Test
	@DisplayName("Flow 3 Failure Path: Unenrolled student cannot access quiz (403 Forbidden)")
	void quizLifecycleFlow_unenrolledStudentCannotAccessQuiz() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m3-unauth-m-" + unique + "@example.org";
		String studentEmail = "flow-m3-unauth-s-" + unique + "@example.org";

		auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);

		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Quiz quiz = quizzes.saveAndFlush(Quiz.builder()
				.week(week)
				.title("Restricted Quiz " + unique)
				.description("Enrolled only")
				.durationMinutes(20)
				.status(QuizStatus.PUBLISHED)
				.build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		// Fetch questions fails with 403 Forbidden
		mockMvc.perform(get("/api/v1/question/student/fetch/{quizId}", quiz.getId())
						.header("Authorization", studentToken))
				.andExpect(status().isForbidden());

		// Submitting fails with 403 Forbidden
		mockMvc.perform(post("/api/v1/submit-quiz-answer/{quizId}", quiz.getId())
						.header("Authorization", studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"answers\":[]}"))
				.andExpect(status().isForbidden());
	}
}
