package com.example.gradproj.EduNest.controller.gateway;

import com.example.gradproj.EduNest.testing.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [EB-112][P5-06] Integration test for the gateway routes on the full H2
 * test context (IntegrationTestBase doctrine).
 *
 * Pins the public HTTP contract end to end:
 * <ul>
 *   <li>Content negotiation on {@code GET /}: HTML for browsers
 *       (Accept: text/html), JSON model for API clients.</li>
 *   <li>Audience filtering via {@code ?audience=} (default technical).</li>
 *   <li>{@code /view}, {@code /diagrams}, {@code /status} and their
 *       {@code /api/v1/showcase/**} aliases.</li>
 *   <li>The apiSurface is LIVE reflected data: non-empty here (real MVC
 *       context) and every path is under {@code /api/**}.</li>
 * </ul>
 */
@Tag("gateway")
class GatewayControllerTest extends IntegrationTestBase {

	@Test
	@DisplayName("GET / with Accept: text/html → the HTML showcase shell")
	void rootServesHtmlToBrowsers() throws Exception {
		MvcResult result = mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("EduNest Backend");
		assertThat(body).contains("/api/v1/showcase"); // the shell fetches the live JSON model
	}

	@Test
	@DisplayName("GET / without HTML accept → full JSON model (technical)")
	void rootServesJsonToApiClients() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.service").value("EduNest Backend"))
				.andExpect(jsonPath("$.version").exists())
				.andExpect(jsonPath("$.commit").exists())
				.andExpect(jsonPath("$.domains").isArray())
				.andExpect(jsonPath("$.diagrams.length()").value(9))
				.andExpect(jsonPath("$.websocket.connectAuth.canonical").isNotEmpty())
				.andExpect(jsonPath("$.metrics.testsFailing").value(0));
	}

	@Test
	@DisplayName("audience filtering through the route: ?audience=hr strips the technical surface")
	void audienceFilteringWorksThroughHttp() throws Exception {
		mockMvc.perform(get("/api/v1/showcase").param("audience", "hr"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.domains.length()").value(3))
				.andExpect(jsonPath("$.websocket").doesNotExist())
				.andExpect(jsonPath("$.apiSurface.length()").value(0));

		mockMvc.perform(get("/api/v1/showcase").param("audience", "business"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.domains.length()").value(10))
				.andExpect(jsonPath("$.websocket").exists())
				// business keeps the live API surface (only hr has it hidden)
				.andExpect(jsonPath("$.apiSurface.length()").value(
						org.hamcrest.Matchers.greaterThan(0)));
	}

	@Test
	@DisplayName("GET /view → shareable HTML (same shell, no negotiation)")
	void viewServesHtml() throws Exception {
		mockMvc.perform(get("/view"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("EduNest")));
	}

	@Test
	@DisplayName("GET /diagrams (and alias) → exactly the 9 documented diagrams")
	void diagramsAndAliasServeAllNine() throws Exception {
		for (String route : new String[]{"/diagrams", "/api/v1/showcase/diagrams"}) {
			mockMvc.perform(get(route))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(9))
					.andExpect(jsonPath("$[0].id").value("system-context"))
					.andExpect(jsonPath("$[0].code").isNotEmpty());
		}
	}

	@Test
	@DisplayName("GET /status (and alias) → runtime metadata fields")
	void statusAndAliasServeRuntimeMetadata() throws Exception {
		for (String route : new String[]{"/status", "/api/v1/showcase/status"}) {
			mockMvc.perform(get(route))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.service").value("EduNest Backend"))
					.andExpect(jsonPath("$.version").exists())
					.andExpect(jsonPath("$.environment").exists())
					.andExpect(jsonPath("$.commit").exists())
					.andExpect(jsonPath("$.uptimeSeconds").isNumber())
					.andExpect(jsonPath("$.timestamp").isNotEmpty());
		}
	}

	@Test
	@DisplayName("apiSurface is live reflected data: non-empty, all under /api/**, public flag present")
	void apiSurfaceIsReflectedFromRealMappings() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/showcase"))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		JsonNode root = new ObjectMapper().readTree(body);
		var surface = root.path("apiSurface");
		assertThat(surface.isArray()).isTrue();
		assertThat(surface.size()).isPositive();
		for (int i = 0; i < surface.size(); i++) {
			assertThat(surface.get(i).path("path").asText()).startsWith("/api");
			assertThat(surface.get(i).path("method").asText()).isNotBlank();
			assertThat(surface.get(i).path("controller").asText()).isNotBlank();
			assertThat(surface.get(i).path("publicRoute").isBoolean()).isTrue();
		}
	}

	@Test
	@DisplayName("unknown audience values fall back to the full technical model")
	void unknownAudienceFallsBackToTechnical() throws Exception {
		mockMvc.perform(get("/api/v1/showcase").param("audience", "nonsense"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.domains.length()").value(10))
				.andExpect(jsonPath("$.websocket").exists());
	}
}
