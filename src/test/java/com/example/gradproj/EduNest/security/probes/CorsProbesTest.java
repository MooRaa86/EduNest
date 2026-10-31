package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-23 / EB-30 / D-23 — CORS probes verifying the approved D-23 contract:
 * - Public cross-origin access allowed with wildcard origin (*).
 * - Bearer authorization via Authorization header (no cookie credentials).
 * - allowCredentials is false / omitted (no wildcard-plus-credentials misconfiguration).
 * - Exposed headers include Authorization.
 * - Preflight OPTIONS handles max-age (3600s), allowed methods, and allowed headers.
 */
@Tag("critical")
class CorsProbesTest extends ProdChainIntegrationTestBase {

	@Autowired
	private AuthFixture auth;

	private static final String ADMIN_EMAIL = "probe-cors-admin@example.org";

	@BeforeEach
	void seed() {
		auth.admin(ADMIN_EMAIL);
	}

	@Test
	@DisplayName("P1-23 / D-23: preflight OPTIONS on public endpoint returns wildcard origin and no credentials")
	void preflightPublicEndpoint() throws Exception {
		mockMvc.perform(options("/api/v1/contact/save-message")
						.header(HttpHeaders.ORIGIN, "https://frontend.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	@DisplayName("P1-23 / D-23: preflight OPTIONS on secured endpoint returns 200 without requiring auth credentials")
	void preflightSecuredEndpointWithoutAuth() throws Exception {
		mockMvc.perform(options("/api/v1/contact/all-messages")
						.header(HttpHeaders.ORIGIN, "https://anywhere.org")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	@DisplayName("P1-23 / D-23: actual cross-origin public request exposes Authorization header and omits allow-credentials")
	void actualRequestPublicEndpointWithOrigin() throws Exception {
		mockMvc.perform(get("/v3/api-docs")
						.header(HttpHeaders.ORIGIN, "https://frontend.example.com"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Authorization")))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	@DisplayName("P1-23 / D-23: actual cross-origin request on secured route without token returns 401 with CORS headers preserved")
	void actualRequestSecuredEndpointWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/contact/all-messages")
						.header(HttpHeaders.ORIGIN, "https://attacker.org"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	@DisplayName("P1-23 / D-23: actual cross-origin request with bearer token returns 200, exposed headers, and no allow-credentials")
	void actualRequestSecuredEndpointWithToken() throws Exception {
		String token = auth.authToken(ADMIN_EMAIL, AuthFixture.ADMIN);
		mockMvc.perform(get("/api/v1/contact/all-messages")
						.header(HttpHeaders.ORIGIN, "https://app.example.com")
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Authorization")))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}
}
