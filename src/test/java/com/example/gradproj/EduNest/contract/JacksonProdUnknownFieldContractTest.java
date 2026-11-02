package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-06 / EB-40 — API contract test: Jackson strictness, PROD side (API-05).
 *
 * Prod runs {@code spring.jackson.deserialization.fail-on-unknown-properties=true}
 * ({@code application-prod.properties}) — unknown JSON fields are a REJECTED
 * request, not a silent data loss hazard. This suite pins the documented
 * contract (error-responses.md catalog): prod unknown field →
 * <b>400 ERR_UNKNOWN_FIELD</b> with {@code fields.invalidField} +
 * {@code fields.allowedFields}.
 *
 * Reconciliation note (the actual reason this test exists): before EB-40 the
 * dedicated {@code UnrecognizedPropertyException} handler was UNREACHABLE —
 * Spring wraps every Jackson deserialization failure into
 * {@code HttpMessageNotReadableException}, so unknown fields answered the
 * generic malformed shape. {@code GlobalExceptionHandler#handleJsonParseException}
 * now dispatches that cause to the documented ERR_UNKNOWN_FIELD contract; this
 * suite holds it. Dev-parity companion:
 * {@code JacksonDevUnknownFieldContractTest} (dev must KEEP ignoring unknown
 * fields — flip both together and the parity guard fails).
 */
@Tag("critical")
class JacksonProdUnknownFieldContractTest extends ProdChainIntegrationTestBase {

	private static final String CONTACT = "/api/v1/contact/save-message";

	@Test
	@DisplayName("P2-06/API-05 (prod): unknown JSON field → 400 ERR_UNKNOWN_FIELD with allowedFields hint")
	void prodUnknownField_rejectedWithCatalogContract() throws Exception {
		mockMvc.perform(post(CONTACT)
						.contentType("application/json")
						.content("{\"name\":\"Contract Probe\",\"email\":\"probe-mail@example.org\"," +
								"\"phone\":\"01012345678\",\"message\":\"hi\",\"unexpected\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_UNKNOWN_FIELD"))
				.andExpect(jsonPath("$.message").value("Field 'unexpected' is not allowed."))
				.andExpect(jsonPath("$.fields.invalidField").value("unexpected"))
				.andExpect(jsonPath("$.fields.allowedFields").value(org.hamcrest.Matchers.containsString("name")));
	}

	@Test
	@DisplayName("P2-06/API-05 (prod): clean body unaffected → 201 (strictness only bites unknown fields)")
	void prodCleanBody_stillCreated() throws Exception {
		mockMvc.perform(post(CONTACT)
						.contentType("application/json")
						.content("{\"name\":\"Contract Probe 2\",\"email\":\"probe-mail2@example.org\"," +
								"\"phone\":\"01012345679\",\"message\":\"clean\"}"))
				.andExpect(status().isCreated());
	}
}
