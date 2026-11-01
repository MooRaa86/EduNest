package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-03 / EB-10 (SEL-16) & P2-03 / EB-37:
 * Public-route matrix AS CODED in the single prod chain (P1-02 collapse, EB-9)
 * and unified PublicRoutesPolicy.
 *
 * Verifies that:
 * 1. Anonymous callers can access public routes and are rejected on protected routes (401).
 * 2. Stale/expired/garbage tokens on public routes proceed anonymously (200 / 201 / 404 resource, not 401).
 * 3. Stale/expired/garbage tokens on protected routes are rejected immediately (401 ERR_INVALID_TOKEN).
 * 4. Valid tokens on dual-mode public routes correctly populate the SecurityContext.
 * 5. Valid tokens on protected routes succeed according to role authorization rules.
 */
@Tag("critical")
class PublicRouteMatrixProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String STUDENT_ANY = "probe-matrix-student@example.org";
	private static final String ADMIN_ANY = "probe-matrix-admin@example.org";
	private static final String MENTOR_ANY = "probe-matrix-mentor@example.org";

	private static final String STALE_RAW_JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdGFsZSIsImV4cCI6MTYwMDAwMDAwMH0.invalid_signature";
	private static final String STALE_BEARER_JWT = "Bearer " + STALE_RAW_JWT;
	private static final String GARBAGE_TOKEN = "totally-malformed-garbage-header-token";

	private Long activeMentorshipId;

	@BeforeEach
	void seed() {
		auth.student(STUDENT_ANY);
		auth.admin(ADMIN_ANY);
		activeMentorshipId = auth.mentorship(MENTOR_ANY, Status.ACTIVE).mentorship().getId();
	}

	// ---------- ANONYMOUS boundaries (matrix: permitAll) ----------

	@Test
	@DisplayName("P1-03/pin: /v3/api-docs public → 200 for anonymous")
	void apiDocsPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("P1-03/pin: /api/v1/contact/save-message public (valid body → 201)")
	void contactSavePublic() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message")
						.contentType("application/json")
						.content("{\"name\":\"probe\",\"email\":\"probe@example.org\","
								+ "\"phone\":\"01012345678\",\"message\":\"probe contact\"}"))
				.andExpect(status().isCreated());
	}

	// ---------- ANONYMOUS boundaries (matrix: protected → 401 via entry point) ----------

	@Test
	@DisplayName("P1-03/pin: anonymous on /api/v1/contact/all-messages (admin route) → 401")
	void adminContactMessagesAnon401() throws Exception {
		mockMvc.perform(get("/api/v1/contact/all-messages"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P1-03/pin: anonymous on /api/v1/admin → 401")
	void adminListAnon401() throws Exception {
		mockMvc.perform(get("/api/v1/admin"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P1-03/pin: anonymous on /student/profile/** → 401")
	void studentProfileAnon401() throws Exception {
		mockMvc.perform(get("/student/profile/anything"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- ROLE boundaries (matrix: hasRole ADMIN → 403 via access-denied handler) ----------

	@Test
	@DisplayName("P1-03/pin: STUDENT on admin route → 403")
	void adminRouteStudent403() throws Exception {
		mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", auth.authToken(STUDENT_ANY, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	// ---------- authenticated-only zone ----------

	@Test
	@DisplayName("P1-03/pin: anonymous on /settings/** → 401")
	void settingsAnon401() throws Exception {
		mockMvc.perform(get("/settings/anything"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- STALE / GARBAGE TOKEN ON PUBLIC ROUTES (P1-03 / SEL-16) ----------

	@ParameterizedTest(name = "P1-03: stale/garbage token on /v3/api-docs → 200 ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnApiDocsPublic200(String token) throws Exception {
		mockMvc.perform(get("/v3/api-docs")
						.header("Authorization", token))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on /api/v1/contact/save-message → 201 ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnContactSavePublic201(String token) throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message")
						.header("Authorization", token)
						.contentType("application/json")
						.content("{\"name\":\"probe\",\"email\":\"probe@example.org\","
								+ "\"phone\":\"01012345678\",\"message\":\"probe contact\"}"))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on /api/v1/mentorship/explore → 200 ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnExploreMentorshipPublic200(String token) throws Exception {
		mockMvc.perform(get("/api/v1/mentorship/explore")
						.header("Authorization", token))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on /api/v1/mentorship/categories → 200 ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnCategoriesPublic200(String token) throws Exception {
		mockMvc.perform(get("/api/v1/mentorship/categories")
						.header("Authorization", token))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on public upload path → 404 resource (not 401) ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnPublicUploadPathNot401(String token) throws Exception {
		// Nonexistent avatar under public category /uploads/mentor/** bypasses JWT 401
		// and reaches resource handler, yielding 404 rather than 401
		mockMvc.perform(get("/uploads/mentor/nonexistent-probe-avatar.jpg")
						.header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	// ---------- STALE / GARBAGE TOKEN ON PROTECTED ROUTES (Must 401 ERR_INVALID_TOKEN) ----------

	@ParameterizedTest(name = "P1-03: stale/garbage token on /settings/** → 401 ERR_INVALID_TOKEN ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnSettingsProtected401(String token) throws Exception {
		mockMvc.perform(get("/settings/anything")
						.header("Authorization", token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on /api/v1/admin → 401 ERR_INVALID_TOKEN ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnAdminProtected401(String token) throws Exception {
		mockMvc.perform(get("/api/v1/admin")
						.header("Authorization", token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
	}

	@ParameterizedTest(name = "P1-03: stale/garbage token on /api/v1/contact/all-messages → 401 ERR_INVALID_TOKEN ({0})")
	@ValueSource(strings = {STALE_RAW_JWT, STALE_BEARER_JWT, GARBAGE_TOKEN})
	void staleTokenOnContactAllMessagesProtected401(String token) throws Exception {
		mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
	}

	// ---------- DUAL-MODE / AUTHENTICATION CONTEXT ON PUBLIC ROUTES ----------

	@Test
	@DisplayName("P1-03: dual-mode overview with anonymous caller → 200 and unauthenticated view")
	void mentorshipOverviewAnonymous() throws Exception {
		mockMvc.perform(get("/api/v1/student/mentorships/" + activeMentorshipId + "/overview"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll").exists())
				.andExpect(jsonPath("$.apiResponse.mentorship.afterEnroll").doesNotExist());
	}

	@Test
	@DisplayName("P1-03: dual-mode overview with stale token → 200 graceful fallback to unauthenticated view")
	void mentorshipOverviewStaleTokenFallback() throws Exception {
		mockMvc.perform(get("/api/v1/student/mentorships/" + activeMentorshipId + "/overview")
						.header("Authorization", STALE_RAW_JWT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll").exists())
				.andExpect(jsonPath("$.apiResponse.mentorship.afterEnroll").doesNotExist());
	}

	@Test
	@DisplayName("P1-03: dual-mode overview with valid token for enrolled student → 200 and authenticated view")
	void mentorshipOverviewValidTokenEnrolled() throws Exception {
		auth.enroll(activeMentorshipId, STUDENT_ANY);
		mockMvc.perform(get("/api/v1/student/mentorships/" + activeMentorshipId + "/overview")
						.header("Authorization", auth.authToken(STUDENT_ANY, AuthFixture.STUDENT)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.mentorship.afterEnroll").exists())
				.andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll").doesNotExist());
	}

	// ---------- VALID TOKEN ON PROTECTED ROUTE (Regression check) ----------

	@Test
	@DisplayName("P1-03: ADMIN with valid token on /api/v1/contact/all-messages → 200")
	void validTokenAdminOnContactAllMessages200() throws Exception {
		mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", auth.authToken(ADMIN_ANY, AuthFixture.ADMIN)))
				.andExpect(status().isOk());
	}
}
