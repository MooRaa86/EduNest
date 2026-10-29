package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-05 / EB-12 — SEL-14 ERROR-SHAPES AFTER THE UNIFIED CONTRACT (Option A,
 * flipped in same PR). All five producers (advice, entry point, denied
 * handler, JWT filter, catch-all) must emit the ONE envelope:
 * {timestamp, status, error, errorCode, message, path} (+ fields for
 * validation, + refId on sanitized 500) — and NEVER leak internals.
 *
 * D-26: field-level validation detail is a permanent contract — the fields
 * map survives inside the envelope.
 */
@Tag("critical")
class ErrorShapeProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;

	private static final String STUDENT = "probe-errshape-student@example.org";

	/** Producer-agnostic: every error body carries ALL envelope keys. */
	private static void assertEnvelope(String body) {
		assertTrue(body.contains("\"timestamp\""), "envelope: timestamp key");
		assertTrue(body.contains("\"status\""), "envelope: status key");
		assertTrue(body.contains("\"error\""), "envelope: error key");
		assertTrue(body.contains("\"errorCode\""), "envelope: errorCode key");
		assertTrue(body.contains("\"message\""), "envelope: message key");
		assertTrue(body.contains("\"path\""), "envelope: path key");
	}

	private static void assertNoInternalLeak(String body) {
		assertFalse(body.contains("Exception"), "leak check: raw exception class names");
		assertFalse(body.contains("java.lang"), "leak check: java.lang internals");
		assertFalse(body.contains("org.hibernate"), "leak check: hibernate internals");
		assertFalse(body.contains("org.springframework"), "leak check: spring internals");
		assertFalse(body.contains("HibernateException"), "leak check: hibernate exception types");
		assertFalse(body.contains("stack"), "leak check: stack markers");
	}

	@BeforeEach
	void seed() {
		auth.student(STUDENT);
	}

	/**
	 * E1 — UNEXPECTED exceptions: FIXED (was: 409 + raw internal message).
	 * Now: 500 + sanitized generic body + refId; internals only in the log.
	 */
	@Test
	@DisplayName("SEL-14/E1 FIXED: unexpected exception → 500 sanitized generic body + refId, zero leak")
	void e1_unexpectedExceptionSanitized500() throws Exception {
		String body = mockMvc.perform(get("/api/v1/conversation/not-a-number/messages")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isInternalServerError())
				.andReturn().getResponse().getContentAsString();

		assertEnvelope(body);
		assertTrue(body.contains("ERR_INTERNAL_SYSTEM"), "errorCode must be ERR_INTERNAL_SYSTEM");
		assertTrue(body.contains("An unexpected system error occurred"), "sanitized generic message");
		assertTrue(body.contains("\"refId\""), "500 must carry a log-correlation refId");
		assertFalse(body.contains("Failed to convert") || body.contains("MethodArgumentTypeMismatch")
						|| body.contains("NumberFormatException"),
				"leak check: no internal exception detail in the body (path echo of the client's own URI is fine)");
		assertNoInternalLeak(body);
	}

	/**
	 * E2 — role-denied: shape normalized to the envelope (was: its own JSON dialect).
	 */
	@Test
	@DisplayName("SEL-14/E2 FIXED: 403 denied handler → envelope + ERR_FORBIDDEN, clean message")
	void e2_accessDenied403Envelope() throws Exception {
		String body = mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden())
				.andReturn().getResponse().getContentAsString();

		assertEnvelope(body);
		assertTrue(body.contains("ERR_FORBIDDEN"), "errorCode must be ERR_FORBIDDEN");
		assertTrue(body.contains("permission"), "clean friendly message");
		assertFalse(body.contains("EduNest-denied-reason") && body.contains("Authorization failed")
				&& !body.contains("errorCode"), "must not be the old bare dialect");
		assertNoInternalLeak(body);
	}

	/**
	 * E3 — unauthenticated: envelope normalized (was: Spring framework echo in message).
	 */
	@Test
	@DisplayName("SEL-14/E3 FIXED: 401 entry point → envelope + ERR_UNAUTHENTICATED, no framework echo")
	void e3_unauthorized401Envelope() throws Exception {
		String body = mockMvc.perform(get("/api/v1/contact/all-messages"))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertEnvelope(body);
		assertTrue(body.contains("ERR_UNAUTHENTICATED"), "errorCode must be ERR_UNAUTHENTICATED");
		assertFalse(body.contains("Full authentication is required"),
				"zero-leak: framework echo message retired");
		assertNoInternalLeak(body);
	}

	/**
	 * E4 — field-validation detail STAYS (D-26 permanence), nested under
	 * fields inside the envelope. Same shape as any other error; field map preserved.
	 */
	@Test
	@DisplayName("D-26/permanent: validation keeps the fields map inside the envelope → 400 + ERR_VALIDATION")
	void e4_validationFieldsInsideEnvelope() throws Exception {
		String body = mockMvc.perform(post("/api/v1/contact/save-message")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		assertEnvelope(body);
		assertTrue(body.contains("ERR_VALIDATION"), "errorCode must be ERR_VALIDATION");
		assertTrue(body.contains("\"fields\""), "D-26: the fields object must exist");
		assertTrue(body.contains("name") && body.contains("email"),
				"D-26: field-level detail (both keys) is a kept contract");
	}

	/**
	 * E5 — bad JWT supplied (but not structurally broken session) → JWT filter
	 * 401 path with the SAME envelope as the entry point (third dialect retired).
	 */
	@Test
	@DisplayName("SEL-14/E5 FIXED: JWT filter 401 → envelope + ERR_INVALID_TOKEN (same shape as entry point)")
	void e5_jwtFilter401Envelope() throws Exception {
		String body = mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", "not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertEnvelope(body);
		assertTrue(body.contains("ERR_INVALID_TOKEN"), "errorCode must be ERR_INVALID_TOKEN");
		assertNoInternalLeak(body);
	}

	/**
	 * E6 — the THREE 401/403 producers emit the SAME key set (values differ);
	 * pinned against drift forever.
	 */
	@Test
	@DisplayName("SEL-14/E6: three producers (entry point / denied handler / JWT filter) — one envelope key set")
	void e6_producersSameShape() throws Exception {
		String e3 = mockMvc.perform(get("/api/v1/contact/all-messages"))
				.andReturn().getResponse().getContentAsString();
		String e2 = mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andReturn().getResponse().getContentAsString();
		String e5 = mockMvc.perform(get("/api/v1/contact/all-messages")
						.header("Authorization", "not-a-real-token"))
				.andReturn().getResponse().getContentAsString();

		for (String key : new String[]{"timestamp", "status", "error", "errorCode", "message", "path"}) {
			assertTrue(e3.contains("\"" + key + "\"") && e2.contains("\"" + key + "\"")
							&& e5.contains("\"" + key + "\""),
					"all three producers must carry the key: " + key);
		}
	}
}
