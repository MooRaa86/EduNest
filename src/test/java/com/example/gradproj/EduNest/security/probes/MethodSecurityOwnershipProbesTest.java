package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-10 / EB-17 (SEL-09):
 * Method-security and ownership sweep for:
 * 1. AdminBadgeController (@PreAuthorize("hasRole('ADMIN')"))
 * 2. MentorDashboardController (@PreAuthorize("hasRole('MENTOR')"))
 * 3. Mentorship-specific dashboard reads (ownership checks → 403 Forbidden for foreign mentors)
 */
@Tag("critical")
class MethodSecurityOwnershipProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String ADMIN_USER = "p1-10-admin@example.org";
	private static final String OWNER_MENTOR = "p1-10-owner-mentor@example.org";
	private static final String FOREIGN_MENTOR = "p1-10-foreign-mentor@example.org";
	private static final String STUDENT_USER = "p1-10-student@example.org";

	private Long ownedMentorshipId;

	@BeforeEach
	void seed() {
		auth.admin(ADMIN_USER);
		auth.student(STUDENT_USER);
		auth.mentor(FOREIGN_MENTOR);
		ownedMentorshipId = auth.mentorship(OWNER_MENTOR, Status.ACTIVE).mentorship().getId();
	}

	// ========== ADMIN BADGE CONTROLLER (SEL-09) ==========

	@Test
	@DisplayName("P1-10: anonymous on admin badges GET /api/admin/badges → 401")
	void adminBadges_anonymous_unauthorized() throws Exception {
		mockMvc.perform(get("/api/admin/badges"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P1-10: student on admin badges GET /api/admin/badges → 403")
	void adminBadges_student_forbidden() throws Exception {
		mockMvc.perform(get("/api/admin/badges")
						.header("Authorization", auth.authToken(STUDENT_USER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: student on admin badges POST /api/admin/badges → 403")
	void adminBadgesCreate_student_forbidden() throws Exception {
		mockMvc.perform(post("/api/admin/badges")
						.header("Authorization", auth.authToken(STUDENT_USER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"title\":\"probe\",\"description\":\"probe\",\"icon\":\"badge.png\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: admin on admin badges GET /api/admin/badges → 200")
	void adminBadges_admin_ok() throws Exception {
		mockMvc.perform(get("/api/admin/badges")
						.header("Authorization", auth.authToken(ADMIN_USER, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}

	// ========== MENTOR DASHBOARD CONTROLLER (SEL-09) ==========

	@Test
	@DisplayName("P1-10: anonymous on mentor dashboard GET /api/v1/dashboard/cards → 401")
	void mentorDashboard_anonymous_unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/cards"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P1-10: student on mentor dashboard GET /api/v1/dashboard/cards → 403")
	void mentorDashboardCards_student_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/cards")
						.header("Authorization", auth.authToken(STUDENT_USER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: student on mentor dashboard GET /api/v1/dashboard/revenue-chart → 403")
	void mentorDashboardRevenueChart_student_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/revenue-chart")
						.header("Authorization", auth.authToken(STUDENT_USER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: student on mentor dashboard GET /api/v1/dashboard/students → 403")
	void mentorDashboardStudents_student_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/students")
						.header("Authorization", auth.authToken(STUDENT_USER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: mentor on mentor dashboard GET /api/v1/dashboard/cards → 200")
	void mentorDashboardCards_mentor_ok() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/cards")
						.header("Authorization", auth.authToken(OWNER_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
	}

	// ========== MENTORSHIP-SPECIFIC DASHBOARD OWNERSHIP (SEL-09 / TD-07) ==========

	@Test
	@DisplayName("P1-10: foreign mentor on GET /api/v1/dashboard/{id}/stats → 403")
	void mentorshipStats_foreignMentor_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/" + ownedMentorshipId + "/stats")
						.header("Authorization", auth.authToken(FOREIGN_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: foreign mentor on GET /api/v1/dashboard/mentorship/{id} → 403")
	void mentorshipFullDashboard_foreignMentor_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/mentorship/" + ownedMentorshipId)
						.header("Authorization", auth.authToken(FOREIGN_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: foreign mentor on GET /api/v1/dashboard/{id}/reviews → 403")
	void mentorshipReviews_foreignMentor_forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/" + ownedMentorshipId + "/reviews")
						.header("Authorization", auth.authToken(FOREIGN_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: owner mentor on GET /api/v1/dashboard/{id}/stats → 200")
	void mentorshipStats_ownerMentor_ok() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/" + ownedMentorshipId + "/stats")
						.header("Authorization", auth.authToken(OWNER_MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
	}
}
