package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-09 / EB-16 — admin bootstrap exposure + guarded bootstrap.
 *
 * Codifies the implemented rulings:
 *  - bootstrap endpoint is PERMITTED anonymously at the chain (by design) but the
 *    business rule makes it one-shot: rejected once any admin exists (400, audited text);
 *  - the register response NEVER returns the submitted plaintext password, and the
 *    admin list NEVER exports the stored hash;
 *  - deleting the LAST admin is rejected (closing the bootstrap re-open hole);
 *  - deleting a non-last admin still works.
 */
@Tag("critical")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminBootstrapProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private AdminRepository admins;

	private static final String BOOTSTRAP_EMAIL = "bootstrap-admin@example.org";
	private static final String SECOND_ADMIN_EMAIL = "second-admin@example.org";
	private static final String FIRST_ADMIN_EMAIL = "first-admin@example.org";
	private static final String CLASH_STUDENT_EMAIL = "clash-user@example.org";
	private static final String BOOTSTRAP_PASSWORD = "Bootstrap-Pass-9";

	private void wipeAdmins() {
		admins.deleteAllInBatch();
	}

	private String bootstrapBody(String email) {
		return "{\"email\":\"" + email + "\",\"firstName\":\"probe\",\"lastName\":\"bootstrap\","
				+ "\"password\":\"" + BOOTSTRAP_PASSWORD + "\"}";
	}

	private int createAdmin(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/register")
						.contentType("application/json").content(bootstrapBody(email)))
				.andReturn().getResponse().getStatus();
	}

	@Test
	@Order(1)
	@DisplayName("P1-09/a: bootstrap succeeds at zero admins, response carries NO credentials")
	void a_bootstrapSucceedsFromColdState() throws Exception {
		wipeAdmins();
		String content = mockMvc.perform(post("/api/v1/admin/register")
						.contentType("application/json").content(bootstrapBody(BOOTSTRAP_EMAIL)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.apiResponse.admin.email").value(BOOTSTRAP_EMAIL))
				.andReturn().getResponse().getContentAsString();

		// The submitted plaintext (and the word "password" entirely) is absent.
		assertThat(content).doesNotContain(BOOTSTRAP_PASSWORD);
		assertThat(content).doesNotContain("password");
		assertThat(content).doesNotContain("bcrypt");
	}

	@Test
	@Order(2)
	@DisplayName("P1-09/b: bootstrap is one-shot — after any admin exists → 400, audited text")
	void b_bootstrapRejectedOnceAdminExists() throws Exception {
		assertThat(admins.count()).as("order-1 left exactly one admin").isGreaterThanOrEqualTo(1);
		String content = mockMvc.perform(post("/api/v1/admin/register")
						.contentType("application/json").content(bootstrapBody(SECOND_ADMIN_EMAIL)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("Admin bootstrap is no longer available."))
				.andReturn().getResponse().getContentAsString();
		assertThat(content).doesNotContain("get out from here");
	}

	@Test
	@Order(3)
	@DisplayName("P1-09/c: email already registered under another role → 400, audited text")
	void c_bootstrapRejectedOnEmailClash() throws Exception {
		wipeAdmins();
		auth.student(CLASH_STUDENT_EMAIL);
		mockMvc.perform(post("/api/v1/admin/register")
						.contentType("application/json").content(bootstrapBody(CLASH_STUDENT_EMAIL)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("Email is already in use."));
	}

	@Test
	@Order(4)
	@DisplayName("P1-09/d: deleting the LAST admin is rejected — bootstrap can never re-open")
	void d_lastAdminDeletionRejected() throws Exception {
		wipeAdmins();
		assertThat(createAdmin(FIRST_ADMIN_EMAIL)).isEqualTo(201);
		String raw = auth.authToken(FIRST_ADMIN_EMAIL, AuthFixture.ADMIN);
		String content = mockMvc.perform(delete("/api/v1/admin/" + FIRST_ADMIN_EMAIL)
						.header("Authorization", raw))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
				.andExpect(jsonPath("$.message").value("Cannot delete the last remaining admin."))
				.andReturn().getResponse().getContentAsString();

		assertThat(admins.findByEmail(FIRST_ADMIN_EMAIL)).as("last admin must survive").isPresent();
		assertThat(content).doesNotContain(BOOTSTRAP_PASSWORD);
	}

	@Test
	@Order(5)
	@DisplayName("P1-09/e: deleting a NON-last admin still works, then the last one is protected")
	void e_nonLastDeletionStillWorks() throws Exception {
		wipeAdmins();
		assertThat(createAdmin(FIRST_ADMIN_EMAIL)).isEqualTo(201);
		auth.admin(SECOND_ADMIN_EMAIL); // fixture admin → count = 2

		String raw = auth.authToken(FIRST_ADMIN_EMAIL, AuthFixture.ADMIN);
		mockMvc.perform(delete("/api/v1/admin/" + SECOND_ADMIN_EMAIL)
						.header("Authorization", raw))
				.andExpect(status().isOk());

		// now back to exactly one admin — deleting it must be refused
		mockMvc.perform(delete("/api/v1/admin/" + FIRST_ADMIN_EMAIL)
						.header("Authorization", raw))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cannot delete the last remaining admin."));
	}

	@Test
	@Order(6)
	@DisplayName("P1-09/f: admin list never exports stored hashes")
	void f_adminListHasNoHashes() throws Exception {
		wipeAdmins();
		assertThat(createAdmin(FIRST_ADMIN_EMAIL)).isEqualTo(201);
		auth.admin(SECOND_ADMIN_EMAIL);
		String raw = auth.authToken(FIRST_ADMIN_EMAIL, AuthFixture.ADMIN);
		String content = mockMvc.perform(get("/api/v1/admin")
						.header("Authorization", raw))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(content).as("no hash material may leave the API").doesNotContain("bcrypt");
		assertThat(content).doesNotContain("password");
	}

	@Test
	@Order(7)
	@DisplayName("P1-09/g: anonymous access to the admin LIST is still 401 at the chain")
	void g_adminListAnonymousDenied() throws Exception {
		mockMvc.perform(get("/api/v1/admin")).andExpect(status().isUnauthorized());
	}
}
