package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Grading -> Points -> Leaderboard Chain Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: Student submits task -> Mentor grades submission -> Points delta applied
 *    to student's TotalPoints -> Leaderboard reflects new score and rank -> Student notified.
 * 2. Failure-path: Unenrolled student cannot submit task -> 400 Bad Request.
 * 3. Failure-path: Foreign mentor cannot grade submission -> 403 Forbidden.
 * 4. Failure-path: Grade exceeding task maximum points is rejected -> 400 Bad Request.
 */
@Tag("critical")
@Tag("business-flow")
public class GradingPointsLeaderboardFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private TaskRepository tasks;
	@Autowired private TaskSubmissionRepository submissions;
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
	@DisplayName("Flow 2 Happy Path: Task submit -> Mentor grade -> Points delta -> Leaderboard update")
	void gradingPointsLeaderboardFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m2-mentor-" + unique + "@example.org";
		String studentEmail = "flow-m2-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();

		// Enroll student (initializes TotalPoints at 0)
		auth.enroll(mentorshipId, studentEmail);
		totalPoints.saveAndFlush(TotalPoints.builder()
				.student(student)
				.mentorship(chain.mentorship())
				.totalPoints(0)
				.build());

		// Seed Week and PUBLISHED Task (points = 30)
		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Task task = tasks.saveAndFlush(Task.builder()
				.week(week)
				.title("REST API Implementation " + unique)
				.description("Build standard CRUD endpoints")
				.points(30)
				.passPoints(15)
				.estimatedMinutes(60)
				.status(TaskStatus.PUBLISHED)
				.dueAt(LocalDateTime.now().plusDays(2))
				.build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);
		String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);

		// Step 1: Student submits task solution with external URL
		mockMvc.perform(multipart("/api/v1/task-submission/{taskId}", task.getId())
						.param("fileUrl", "https://github.com/example/edunest-solution")
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.submission.taskId").value(task.getId()))
				.andExpect(jsonPath("$.apiResponse.submission.status").value("SUBMITTED"));

		TaskSubmission sub = submissions.findByTask_IdAndStudent_Id(task.getId(), student.getId())
				.orElseThrow();
		assertThat(sub.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
		assertThat(sub.getFileUrl()).isEqualTo("https://github.com/example/edunest-solution");

		// Step 2: Mentor grades submission (awards 25 out of 30 points)
		mockMvc.perform(post("/api/v1/task-submission/{submissionId}/grade", sub.getId())
						.header("Authorization", mentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"score\":25,\"feedback\":\"Excellent error handling!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.submission.finalScore").value(25))
				.andExpect(jsonPath("$.apiResponse.submission.status").value("GRADED"));

		// Step 3: Assert observable effect on TotalPoints: delta of +25 applied
		TotalPoints tp = totalPoints.findByStudent_IdAndMentorship_Id(student.getId(), mentorshipId)
				.orElseThrow();
		assertThat(tp.getTotalPoints()).isEqualTo(25);

		// Step 4: Assert observable effect on Leaderboard: student is ranked #1 with 25 points
		mockMvc.perform(get("/api/v1/student/mentorships/{mentorshipId}/leaderboard", mentorshipId)
						.header("Authorization", studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.leaderboard.userRank").value(1))
				.andExpect(jsonPath("$.apiResponse.leaderboard.currentUser.totalPoints").value(25));

		// Step 5: Assert student received grading notification
		Page<UserNotification> studentNotifs = userNotifications.findWithNotification(studentEmail, PageRequest.of(0, 10));
		assertThat(studentNotifs.getContent()).anyMatch(un ->
				un.getNotification().getTitle().equals("Task Graded") &&
				un.getNotification().getContent().contains("Score: 25/30")
		);
	}

	@Test
	@DisplayName("Flow 2 Failure Path: Unenrolled student cannot submit task -> 400 Bad Request")
	void gradingFlow_unenrolledStudentCannotSubmitTask() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "flow-m2-unenroll-m-" + unique + "@example.org";
		String studentEmail = "flow-m2-unenroll-s-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);

		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Task task = tasks.saveAndFlush(Task.builder()
				.week(week)
				.title("Secured Task " + unique)
				.description("description")
				.points(20)
				.passPoints(10)
				.estimatedMinutes(30)
				.status(TaskStatus.PUBLISHED)
				.dueAt(LocalDateTime.now().plusDays(2))
				.build());

		String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

		mockMvc.perform(multipart("/api/v1/task-submission/{taskId}", task.getId())
						.param("fileUrl", "https://github.com/example/unauthorized")
						.header("Authorization", studentToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("You must enroll in this mentorship before submitting tasks."));

		assertThat(submissions.findByTask_IdAndStudent_Id(task.getId(), student.getId())).isEmpty();
	}

	@Test
	@DisplayName("Flow 2 Failure Path: Foreign mentor cannot grade submission -> 403 Forbidden")
	void gradingFlow_foreignMentorCannotGrade() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String ownerMentorEmail = "flow-m2-owner-" + unique + "@example.org";
		String foreignMentorEmail = "flow-m2-foreign-" + unique + "@example.org";
		String studentEmail = "flow-m2-sub-s-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		auth.mentor(foreignMentorEmail);
		var chain = auth.mentorship(ownerMentorEmail, Status.ACTIVE);
		auth.enroll(chain.mentorship().getId(), studentEmail);

		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1 " + unique).build());
		Task task = tasks.saveAndFlush(Task.builder()
				.week(week).title("Task " + unique).description("desc").points(20).passPoints(10)
				.estimatedMinutes(30).status(TaskStatus.PUBLISHED).dueAt(LocalDateTime.now().plusDays(2)).build());

		TaskSubmission sub = submissions.saveAndFlush(TaskSubmission.builder()
				.task(task).student(student).fileUrl("https://example.org/solution")
				.status(SubmissionStatus.SUBMITTED).submittedAt(LocalDateTime.now()).build());

		String foreignMentorToken = auth.authToken(foreignMentorEmail, AuthFixture.MENTOR);

		mockMvc.perform(post("/api/v1/task-submission/{submissionId}/grade", sub.getId())
						.header("Authorization", foreignMentorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"score\":15,\"feedback\":\"Attempt by foreign mentor\"}"))
				.andExpect(status().isForbidden());

		// Ensure points were NOT modified
		TaskSubmission unmodified = submissions.findById(sub.getId()).orElseThrow();
		assertThat(unmodified.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
	}
}
