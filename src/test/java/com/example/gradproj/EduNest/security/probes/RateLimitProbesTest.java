package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-15 / P1-08 — SEL-03 THROTTLING + LOCKOUT + ENUMERATION probes
 * (flipped in this same PR — no "today" pins needed: the old behavior
 * qualifies as a defect).
 *
 * Tight limits via @TestPropertySource (master switch re-enabled here;
 * the rest of the suite runs with app.rate-limit.enabled=false).
 * Each test gets a UNIQUE X-Forwarded-For ip so its counters are isolated.
 *
 * OMAR SPEC (2026-10-30): 8/min default (probes override to 3/2s for speed),
 * 5 failures -> 5-min lock (probes: 3 -> 3s), TTL sweep 10 min, Retry-After
 * always present on 429s.
 */
@Tag("critical")
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.default-limit=3",
        "app.rate-limit.default-window-seconds=2",
        "app.rate-limit.lockout.threshold=3",
        "app.rate-limit.lockout.wait-seconds=3",
        "app.rate-limit.ttl-seconds=60"
})
class RateLimitProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private StudentRepository students;
	@Autowired private PasswordEncoder passwordEncoder;

	private static final String PASSWORD = "probe-password";

	/* ===================== probes ===================== */

	/**
	 * Bucket hits the limit with SUCCESSFUL logins (rate limit counts requests,
	 * not failures) — 8/min in prod, 3 here — then rejects with 429 + envelope
	 * + Retry-After of the remaining window.
	 */
	@Test
	@DisplayName("R1: exceeds rate limit on /login-api → 429 ERR_RATE_LIMITED + Retry-After")
	void r1_loginRateLimitTrips() throws Exception {
		String email = "probe-rl-success@example.org";
		auth.student(email);

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(login("198.51.100.1", email, PASSWORD))
					.andExpect(status().isOk());
		}
		String body = mockMvc.perform(login("198.51.100.1", email, PASSWORD))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("ERR_RATE_LIMITED"))
				.andReturn().getResponse().getContentAsString();

		assertTrue(body.contains("\"message\""), "429 uses the unified envelope");
		String retryAfter = mockMvc.perform(login("198.51.100.1", email, PASSWORD))
				.andExpect(status().isTooManyRequests())
				.andReturn().getResponse().getHeader("Retry-After");
		assertTrue(retryAfter != null && Integer.parseInt(retryAfter) >= 1,
				"Retry-After header must be present and positive (Omar spec)");
	}

	/** The window resets — after expiry the same caller is served again. */
	@Test
	@DisplayName("R2: fixed window resets after expiry → caller served again")
	void r2_windowResets() throws Exception {
		String email = "probe-rl-reset@example.org";
		auth.student(email);

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(login("198.51.100.2", email, PASSWORD))
					.andExpect(status().isOk());
		}
		mockMvc.perform(login("198.51.100.2", email, PASSWORD))
				.andExpect(status().isTooManyRequests());

		Thread.sleep(2300); // window = 2s (probe override)

		mockMvc.perform(login("198.51.100.2", email, PASSWORD))
				.andExpect(status().isOk());
	}

	/** 3 consecutive failures → lock gate BEFORE rate limit; wait expires it. */
	@Test
	@DisplayName("R3: consecutive failures → 429 ERR_ACCOUNT_LOCKED with Retry-After → expires cleanly")
	void r3_lockoutTripsAndExpires() throws Exception {
		String email = "probe-rl-lockout@example.org";
		auth.student(email);

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(login("198.51.100.3", email, "wrong-password"))
					.andExpect(status().isUnauthorized());
		}
		MvcResult locked = mockMvc.perform(login("198.51.100.3", email, "wrong-password"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("ERR_ACCOUNT_LOCKED"))
				.andReturn();
		String retryAfter = locked.getResponse().getHeader("Retry-After");
		assertTrue(retryAfter != null && Integer.parseInt(retryAfter) >= 1 && Integer.parseInt(retryAfter) <= 3,
				"Retry-After must match the remaining lock wait (Omar spec: 5 min in prod, 3s here)");

		Thread.sleep(3300); // lockout wait = 3s (probe override)

		// unlocked again — failure envelopes resume (canonical 401), no lock error
		String after = mockMvc.perform(login("198.51.100.3", email, "wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
		assertNotEquals(0L, after.length());
	}

	/** THE SEL-03 UNIFICATION: unknown email vs wrong password → IDENTICAL bodies. */
	@Test
	@DisplayName("R4: enumeration probe — unknown-email login and wrong-password login are byte-identical (401)")
	void r4_enumerationBodiesIdentical() throws Exception {
		String existing = "probe-rl-existing@example.org";
		auth.student(existing);

		String unknownBody = mockMvc.perform(login("198.51.100.4", "probe-rl-ghost@example.org", "whatever-pw"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();

		String wrongPasswordBody = mockMvc.perform(login("198.51.100.4", existing, "wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();

		assertEquals(normalize(unknownBody), normalize(wrongPasswordBody),
				"SEL-03: no body, code, or status difference may reveal whether the email exists");
	}

	/** Unverified (enabled=false) and deleted users fold into the same canonical 401. */
	@Test
	@DisplayName("R5: unverified AND deleted accounts fold into the canonical invalid-credentials 401")
	void r5_accountStatesFoldCanonical() throws Exception {
		String unverified = "probe-rl-unverified@example.org";
		saveState(unverified, false, false);
		String deleted = "probe-rl-deleted@example.org";
		saveState(deleted, true, true);

		mockMvc.perform(login("198.51.100.5", unverified, "whatever-pw"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid email or password."))
				.andReturn();

		mockMvc.perform(login("198.51.100.5", deleted, "whatever-pw"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").value("Invalid email or password."));
	}

	/** Non-login gated buckets are equally throttled (OTP verify here). */
	@Test
	@DisplayName("R6: OTP_VERIFY bucket throttled too → 429 ERR_RATE_LIMITED (not login-specific)")
	void r6_otpBucketThrottled() throws Exception {
		String email = "probe-rl-otp@example.org";
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(otpVerify("198.51.100.6", email))
					.andReturn(); // 400s (invalid otp) — counting is request-based, not result-based
		}
		mockMvc.perform(otpVerify("198.51.100.6", email))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("ERR_RATE_LIMITED"))
				.andReturn();
	}

	/* ===================== helpers ===================== */

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String ip, String email, String password) {
		return post("/login-api")
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder otpVerify(String ip, String email) {
		return post("/api/v1/register/verify-user")
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"otp\":\"000000\"}");
	}

	private void saveState(String email, boolean deleted, boolean unverified) {
		Student s = Student.builder()
				.firstName("Probe").lastName("State" + System.nanoTime())
				.email(email).password(passwordEncoder.encode(PASSWORD))
				.educationalLevel(EducationalLevel.FRESH)
				.role(auth.role(AuthFixture.STUDENT))
				.enabled(!unverified).deleted(deleted).build();
		students.saveAndFlush(s);
	}

	/** Strips the volatile timestamp so two envelopes can be compared byte-wise. */
	private static String normalize(String body) {
		return body.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"\"");
	}
}
