package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4-09 / EB-58 — USR-01: the mentor-side student-projects filter works.
 *
 * Before this ticket, {@code MentorViewStudentProfileController#getStudentProjects}
 * accepted the {@code status} @RequestParam but never passed it to the service —
 * the repository slot ({@code findForStudentProfile(studentId, status, pageable)})
 * existed since P4-07 but always received {@code null}, so the FE filter card was
 * silently broken. This suite pins the threaded contract end-to-end over the
 * prod security chain:
 * <ul>
 *   <li>no filter → all submissions (unchanged default);</li>
 *   <li>{@code status=GRADED} / {@code status=SUBMITTED} → filtered;</li>
 *   <li>{@code size=0} → clamped to 1 (P4-09 clamp on the same surface);</li>
 *   <li>a mentor with no enrollment of the student → 403 (access guard unchanged).</li>
 * </ul>
 */
@Tag("critical")
class MentorStudentProjectsStatusContractTest extends ProdChainIntegrationTestBase {

	private static final String PROJECTS_URL = "/profile/students/students/{studentId}/projects";
	private static final String PROGRESS_URL = "/profile/students/students/{studentId}/mentorships-progress";

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private ProjectRepository projects;
	@Autowired private ProjectSubmissionRepository submissions;

	private String mentorEmail;
	private String otherMentorEmail;
	private java.util.UUID studentPublicId;

	private void seed() {
		mentorEmail = "eb58-usr01-mentor-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org";
		otherMentorEmail = "eb58-usr01-stranger-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org";
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		MentorShip ms = chain.mentorship();
		var student = auth.enroll(ms.getId(), "eb58-usr01-student-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org").getStudent();
		studentPublicId = student.getPublicId();

		Week week = weeks.saveAndFlush(Week.builder().mentorship(ms).title("week").build());
		Project gradedProject = projects.saveAndFlush(Project.builder()
				.week(week).title("graded project").brief("brief").goal("goal")
				.startAt(LocalDateTime.now().minusDays(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.points(10)
				.build());
		Project openProject = projects.saveAndFlush(Project.builder()
				.week(week).title("open project").brief("brief").goal("goal")
				.startAt(LocalDateTime.now().minusDays(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.points(10)
				.build());
		submissions.saveAndFlush(ProjectSubmission.builder()
				.project(gradedProject).student(student).status(SubmissionStatus.GRADED)
				.finalScore(90).submittedAt(LocalDateTime.now()).build());
		submissions.saveAndFlush(ProjectSubmission.builder()
				.project(openProject).student(student).status(SubmissionStatus.SUBMITTED)
				.submittedAt(LocalDateTime.now()).build());
		auth.mentor(otherMentorEmail);
	}

	@Test
	@DisplayName("USR-01: no status param → all submissions (default contract unchanged)")
	void noStatus_returnsAll() throws Exception {
		seed();
		mockMvc.perform(get(PROJECTS_URL, studentPublicId)
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.studentProjects.totalElements").value(2));
	}

	@Test
	@DisplayName("USR-01: status=GRADED → only graded submissions")
	void gradedFilter() throws Exception {
		seed();
		mockMvc.perform(get(PROJECTS_URL, studentPublicId)
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("status", "GRADED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.studentProjects.totalElements").value(1));
	}

	@Test
	@DisplayName("USR-01: status=SUBMITTED → only submitted submissions")
	void submittedFilter() throws Exception {
		seed();
		mockMvc.perform(get(PROJECTS_URL, studentPublicId)
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("status", "SUBMITTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.studentProjects.totalElements").value(1));
	}

	@Test
	@DisplayName("USR-01 + clamp: status=GRADED&size=0 → clamped to size=1 → 200")
	void gradedFilter_withClampedSize() throws Exception {
		seed();
		mockMvc.perform(get(PROJECTS_URL, studentPublicId)
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("status", "GRADED").param("size", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.studentProjects.totalElements").value(1))
				.andExpect(jsonPath("$.apiResponse.studentProjects.size").value(1));
	}

	@Test
	@DisplayName("USR-01 second surface clamp: mentorships-progress size=0 → clamped to 1 → 200")
	void progressSurface_clampedSize() throws Exception {
		seed();
		mockMvc.perform(get(PROGRESS_URL, studentPublicId)
						.header("Authorization", auth.authToken(mentorEmail, AuthFixture.MENTOR))
						.param("size", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorshipProgress.size").value(1));
	}

	@Test
	@DisplayName("USR-01 access guard unchanged: unrelated mentor → 403 ERR_FORBIDDEN")
	void unrelatedMentor_forbidden() throws Exception {
		seed();
		mockMvc.perform(get(PROJECTS_URL, studentPublicId)
						.header("Authorization", auth.authToken(otherMentorEmail, AuthFixture.MENTOR))
						.param("status", "GRADED"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"));
	}
}
