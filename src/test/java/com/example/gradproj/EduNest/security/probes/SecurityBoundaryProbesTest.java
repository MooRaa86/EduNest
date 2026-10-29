package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — SEL-set "absence/boundary" probes: login throttling
 * (P1-08 / EB-15), admin bootstrap exposure (P1-09 / EB-16), reset-flow reachability
 * (P1-07 / EB-14). Each documents TODAY's reality; the flipping EB is in the name.
 */
@Tag("critical")
class SecurityBoundaryProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private OTPRepository otps;
	@Autowired private ObjectMapper objectMapper;

	private static final String ADMIN_EMAIL = "probe-boundary-admin@example.org";
	private static final String VICTIM_EMAIL = "probe-boundary-victim@example.org";

	/**
	 * P1-08 — login throttling DOES NOT EXIST today.
	 * EXPECT TODAY: five bad logins all answer (401-ish), NONE answers 429.
	 * FLIPS WITH: [P1-08] auth throttling (EB-15) → then expect 429 on attempt N+.
	 */
	@Test
	@DisplayName("P1-08/todayOpen: five bad logins → none rate-limited (no 429)")
	void p08_noLoginThrottleExists() throws Exception {
		final String json = "{\"email\":\"" + VICTIM_EMAIL + "\",\"password\":\"wrong-password\"}";
		for (int i = 1; i <= 5; i++) {
			int status = mockMvc.perform(post("/login-api")
							.contentType("application/json").content(json))
					.andReturn().getResponse().getStatus();
			assertThat(status).as("attempt %d of 5 — today: no limiter", i).isNotEqualTo(429);
		}
	}

	/**
	 * P1-09 — admin bootstrap (/api/v1/admin/register) is anonymous-reachable.
	 * EXPECT TODAY: NOT 401/403 (a business 4xx — e.g., 409 when an admin already
	 * exists because other probes seeded one — still proves anonymous reachability).
	 * FLIPS WITH: [P1-09] admin exposure + guarded bootstrap (EB-16).
	 */
	@Test
	@DisplayName("P1-09/todayOpen: admin bootstrap anonymous-reachable → NOT 401/403")
	void p09_adminBootstrapReachableAnonymously() throws Exception {
		String body = "{\"email\":\"" + ADMIN_EMAIL + "\",\"firstName\":\"probe\","
				+ "\"lastName\":\"admin\",\"password\":\"ProbePass123!\"}";
		int status = mockMvc.perform(post("/api/v1/admin/register")
						.contentType("application/json").content(body))
				.andReturn().getResponse().getStatus();

		assertThat(status).as("today: bootstrap open; any auth-related 4xx flips this").isNotEqualTo(401);
		assertThat(status).isNotEqualTo(403);
	}

	/**
	 * P1-07 — legacy verified rows never authorize an email-only reset.
	 */
	@Test
	@DisplayName("P1-07: email-only reset is rejected even with a legacy RESET row")
	void p07_emailOnlyResetRejected() throws Exception {
		var student = auth.student(VICTIM_EMAIL);
		otps.saveAndFlush(OTP.builder().user(student)
				.otpType(OtpType.RESET).otpCode("424242")
				.expiresAt(LocalDateTime.now().plusMinutes(5)).build());

		mockMvc.perform(post("/forget-password/reset")
						.contentType("application/json")
						.content("{\"email\":\"" + VICTIM_EMAIL + "\",\"newPassword\":\"NewProbePass123!\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("P1-07: verified token resets once through the production security chain")
	void p07_verifiedTokenResetsOnce() throws Exception {
		String email = "probe-reset-token@example.org";
		var student = auth.student(email);
		otps.saveAndFlush(OTP.builder().user(student).otpType(OtpType.RESET_PENDING).otpCode("424242")
				.expiresAt(LocalDateTime.now().plusMinutes(2)).build());
		var verification = mockMvc.perform(post("/forget-password/verify-otp").contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"otp\":\"424242\"}"))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(verification.getHeader("Cache-Control")).isEqualTo("no-store");
		String token = objectMapper.readTree(verification.getContentAsString()).path("apiResponse").path("resetToken").asText();
		String request = objectMapper.writeValueAsString(java.util.Map.of("email", email, "resetToken", token,
				"newPassword", "NewProbePass123!"));
		mockMvc.perform(post("/forget-password/reset").contentType("application/json").content(request))
				.andExpect(status().isOk());
		mockMvc.perform(post("/forget-password/reset").contentType("application/json").content(request))
				.andExpect(status().isBadRequest());
	}
}
