package com.example.gradproj.EduNest.security.probes;

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
 * P2-03 / EB-37 — P1-03 public-route matrix AS CODED in the single prod chain
 * (P1-02 collapse, EB-9). These pins hold the WHOLE matrix; when a review task
 * deliberately moves a route class, these markers flip WITH it.
 */
@Tag("critical")
class PublicRouteMatrixProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String STUDENT_ANY = "probe-matrix-student@example.org";

	@BeforeEach
	void seed() {
		auth.student(STUDENT_ANY);
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
}
