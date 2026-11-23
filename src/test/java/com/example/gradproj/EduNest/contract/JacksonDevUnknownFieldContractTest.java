package com.example.gradproj.EduNest.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-06 / EB-40 — API contract test: Jackson strictness, DEV side (API-05).
 *
 * EB-63 enables production's strict unknown-field policy for development too.
 * {@code @ActiveProfiles("test")} without "prod" boots the dev chain
 * (pseudo {@code isProd()} is false → devChain permitAll). This is the parity
 * companion of {@code JacksonProdUnknownFieldContractTest}: both reject unknown
 * fields with the same error contract.
 *
 * Contact save is the shared surface for the pair (public in BOTH chains).
 */
@Tag("critical")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
class JacksonDevUnknownFieldContractTest {

	@Autowired private MockMvc mockMvc;

	@Test
	@DisplayName("EB-63/API-05 (dev): unknown JSON field → 400 ERR_UNKNOWN_FIELD")
	void devUnknownField_rejectedWithCatalogContract() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message")
						.contentType("application/json")
						.content("{\"name\":\"Dev Parity Probe\",\"email\":\"dev-parity@example.org\"," +
								"\"phone\":\"01012345678\",\"message\":\"hi\",\"unexpected\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.errorCode").value("ERR_UNKNOWN_FIELD"))
				.andExpect(jsonPath("$.message").value("Field 'unexpected' is not allowed."))
				.andExpect(jsonPath("$.path").value("/api/v1/contact/save-message"))
				.andExpect(jsonPath("$.fields.invalidField").value("unexpected"))
				.andExpect(jsonPath("$.fields.allowedFields").value("email, message, name, phone"))
				.andExpect(jsonPath("$.refId").doesNotExist());
	}

	@Test
	void devCleanBody_stillCreated() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message").contentType("application/json")
						.content("{\"name\":\"Dev Clean\",\"email\":\"dev-clean@example.org\",\"phone\":\"01012345678\",\"message\":\"hi\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void devMalformedBody_keepsMalformedContract() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message").contentType("application/json").content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_MALFORMED_BODY"));
	}

	@Test
	void devInvalidBody_keepsValidationContract() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message").contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
				.andExpect(jsonPath("$.fields.name").exists());
	}
}
