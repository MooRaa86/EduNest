package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-17 / EB-24 — homepage progress enrollment probes (MSH-11).
 *
 * Contract: {@code GET /api/v1/homepage/progress/{mentorshipId}} reveals a
 * mentorship's private content composition (totals of tasks/quizzes/projects —
 * curriculum structure is only meaningful to members). The endpoint is
 * STUDENT-only (class-level PreAuthorize) AND requires an enrollment of the
 * principal-derived caller email; anyone else gets 403 (AccessDeniedException →
 * GlobalExceptionHandler), anonymous gets the chain's 401.
 *
 * Related surfaces audited (no change needed here):
 * <ul>
 *   <li>The other caller of {@code HomePageService#getStudentMentorshipProgress}
 *       is {@code MentorshipOverviewService#getMentorshipWithEnrollmentStatus} —
 *       only reachable in the afterEnroll branch, already gated by the same
 *       enrollment check; the service-level guard added in EB-24 is identical,
 *       so that path is unchanged (defense in depth).</li>
 *   <li>{@code GET /profile/students/students/{id}/mentorships-progress} (any
 *       MENTOR reads any student's progress list) is a DIFFERENT service
 *       (ProfileService) and ticket — flagged for the backlog, out of P1-17.</li>
 * </ul>
 */
@Tag("critical")
class HomepageProgressEnrollmentProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String MENTOR = "eb24-mentor@example.org";
	private static final String ENROLLED = "eb24-enrolled@example.org";
	private static final String STRANGER = "eb24-stranger@example.org";
	private static final String ADMIN = "eb24-admin@example.org";

	private Long activeId;

	@BeforeEach
	void seed() throws Exception {
		activeId = auth.mentorship(MENTOR, Status.ACTIVE).mentorship().getId();
		auth.enroll(activeId, ENROLLED);
		auth.student(STRANGER);
		auth.admin(ADMIN);
	}

	@Test
	@DisplayName("EB24/[1] anonymous → 401 (route is chain-authenticated, before any logic)")
	void anonymous_unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/homepage/progress/" + activeId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("EB24/[2] non-enrolled STUDENT → 403 (no curriculum-structure leak)")
	void strangerStudent_forbidden() throws Exception {
		String body = mockMvc.perform(get("/api/v1/homepage/progress/" + activeId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
		// the secret totals must not ride along in the error response
		assertThat(body).doesNotContain("totalTasks").doesNotContain("progressPercentage");
	}

	@Test
	@DisplayName("EB24/[3] enrolled STUDENT → 200 with progress payload")
	void enrolledStudent_ok() throws Exception {
		String body = mockMvc.perform(get("/api/v1/homepage/progress/" + activeId)
						.header("Authorization", auth.authToken(ENROLLED, AuthFixture.STUDENT)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).contains("progressPercentage");
	}

	@Test
	@DisplayName("EB24/[4] MENTOR → 403 and ADMIN → 403 (STUDENT-only endpoint, by role)")
	void mentorAndAdmin_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/homepage/progress/" + activeId)
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/homepage/progress/" + activeId)
						.header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("EB24/[5] nonexistent mentorship id → 403 (no existence oracle)")
	void nonexistentId_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/homepage/progress/999999999")
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}
}
