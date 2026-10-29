package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — SEL-14 / P1-05 ERROR-SHAPES AS OF TODAY (Option A).
 * The current handler has FOUR body dialects; the catch-all leaks internals (409).
 * These probes PIN today's shapes so the EB-12 contract work flips them WITH review.
 * (D-26 default: validation field-map stays as-is — the last probe asserts permanence.)
 */
@Tag("critical")
class ErrorShapeProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String STUDENT = "probe-errshape-student@example.org";

	@BeforeEach
	void seed() {
		auth.student(STUDENT);
	}

	/**
	 * E1 — unexpected exceptions → TODAY: 409 with the raw exception.getMessage().
	 * (GlobalExceptionHandler L223-225: generic Exception → CONFLICT + message leak.)
	 * Trigger: type mismatch on a path variable — no dedicated handler → catch-all.
	 * FLIPS WITH: [P1-05] error contract (EB-12) → then expect 500 + sanitized body.
	 */
	@Test
	@DisplayName("SEL-14/todayOpen: unexpected exception → 409 with internal detail (leak)")
	void e1_unexpectedException409WithLeak() throws Exception {
		String body = mockMvc.perform(get("/api/v1/conversation/not-a-number/messages")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isConflict())
				.andReturn().getResponse().getContentAsString();

		assertTrue(body.contains("error"), "today: ErrorResponse dialect, 'error' key present");
	}

	/**
	 * E2 — role-denied (403 writer body as of today).
	 * FLIPS WITH: [P1-05] (EB-12) — body shape normalizes.
	 */
	@Test
	@DisplayName("SEL-14/todayPin: 403 writer answers with a body (shape snapshot)")
	void e2_accessDenied403HasBody() throws Exception {
		String body = mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden())
				.andReturn().getResponse().getContentAsString();
		assertTrue(body != null && !body.isBlank(), "403 must remain a JSON body, never empty");
	}

	/**
	 * E3 — unauthenticated (401 writer body as of today).
	 * FLIPS WITH: [P1-05] (EB-12) — body shape normalizes.
	 */
	@Test
	@DisplayName("SEL-14/todayPin: 401 writer answers with a body (shape snapshot)")
	void e3_unauthorized401HasBody() throws Exception {
		String body = mockMvc.perform(get("/api/v1/contact/all-messages"))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		assertTrue(body != null && !body.isBlank(), "401 must remain a JSON body, never empty");
	}

	/**
	 * E4 — field-validation map STAYS (D-26 decided permanence).
	 * This one should STILL PASS after EB-12: same shape, more statuses confirmed.
	 */
	@Test
	@DisplayName("D-26/permanent: field-validation errors keep the ErrorResponse field map → 400")
	void e4_validationFieldMapStays() throws Exception {
		String body = mockMvc.perform(post("/api/v1/contact/save-message")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		assertTrue(body.contains("name") && body.contains("email"),
				"D-26: field-level validation detail is a kept contract");
	}
}
