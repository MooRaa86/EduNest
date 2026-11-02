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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-06 / EB-40 — API contract test: Jackson strictness, DEV side (API-05).
 *
 * Dev does NOT set {@code fail-on-unknown-properties} (default false) — unknown
 * JSON fields are silently IGNORED there (FE convenience during development).
 * {@code @ActiveProfiles("test")} without "prod" boots the dev chain
 * (pseudo {@code isProd()} is false → devChain permitAll). This is the parity
 * companion of {@code JacksonProdUnknownFieldContractTest}: prod rejects,
 * dev ignores — both flip together, or the API-05 profile-divergence contract
 * is violated.
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
	@DisplayName("P2-06/API-05 (dev): unknown JSON field IGNORED → 201 (parity: prod rejects, dev tolerates)")
	void devUnknownField_ignoredPlsCreated() throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message")
						.contentType("application/json")
						.content("{\"name\":\"Dev Parity Probe\",\"email\":\"dev-parity@example.org\"," +
								"\"phone\":\"01012345678\",\"message\":\"hi\",\"unexpected\":true}"))
				.andExpect(status().isCreated());
	}
}
