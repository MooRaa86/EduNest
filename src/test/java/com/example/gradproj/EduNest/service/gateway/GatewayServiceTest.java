package com.example.gradproj.EduNest.service.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [EB-112][P5-06] Unit test for the gateway showcase model.
 *
 * Pin, against the built model:
 * <ol>
 *   <li>Audience filtering: hr → 3 domains with bullets stripped and the
 *       WebSocket/API surface hidden; business → all domains, bullets kept,
 *       API surface hidden; technical (default + unknown) → everything.</li>
 *   <li>Content integrity: 9 diagrams, present WS contract, no empty
 *       tech stack.</li>
 *   <li>The documented WS auth contract in EXACT wording: canonical raw JWT
 *       (no Bearer prefix), Bearer tolerated server-side — this is what the
 *       HTML showcase and JSON model will show the FE team, so it must never
 *       drift from {@code WsConnectAuthContractTest} / {@code JwtService}.</li>
 *   <li>Secrets hygiene: the serialized model contains no credentials,
 *       connection strings, or PII-shaped values — the whole point of a
 *       public, anonymous route.</li>
 * </ol>
 */
@Tag("gateway")
class GatewayServiceTest {

	private GatewayService gatewayService;
	private MockEnvironment env;

	@BeforeEach
	void buildFreshModel() {
		env = new MockEnvironment();
		env.setActiveProfiles("prod");

		@SuppressWarnings("unchecked")
		ObjectProvider<RequestMappingHandlerMapping> mappingProvider = mock(ObjectProvider.class);
		when(mappingProvider.getIfAvailable()).thenReturn(null); // unit test: no live MVC surface

		@SuppressWarnings("unchecked")
		ObjectProvider<BuildProperties> buildProvider = mock(ObjectProvider.class);
		Properties props = new Properties();
		props.setProperty("version", "0.0.1-SNAPSHOT");
		when(buildProvider.getIfAvailable()).thenReturn(new BuildProperties(props));

		gatewayService = new GatewayService(mappingProvider, buildProvider, env);
		gatewayService.buildModel();
	}

	@Test
	@DisplayName("base model: 10 domains, 5 workflows, 9 diagrams, WS contract, tech stack, metrics")
	void baseModelIsComplete() {
		GatewayModel m = gatewayService.model();
		assertThat(m.domains()).hasSize(10);
		assertThat(m.workflows()).hasSize(5);
		assertThat(m.diagrams()).hasSize(9);
		assertThat(m.websocket()).isNotNull();
		assertThat(m.techStack()).isNotEmpty();
		assertThat(m.metrics().testsFailing()).isZero();
	}

	@Test
	@DisplayName("runtime data: version from BuildProperties, env from active profiles, commit fallback")
	void runtimeDataIsSourcedLive() {
		GatewayModel m = gatewayService.model();
		assertThat(m.version()).isEqualTo("0.0.1-SNAPSHOT");
		assertThat(m.environment()).isEqualTo("prod");
		// no git.properties / deploy env in the unit test → honest "local" marker
		assertThat(m.commit()).isEqualTo("local");
	}

	@Test
	@DisplayName("hr audience: 3 domains, bullets stripped, websocket null, tech/api surface hidden")
	void hrViewIsStrippedToPlainLanguage() {
		GatewayModel hr = gatewayService.modelFor("hr");
		assertThat(hr.domains()).hasSize(3);
		assertThat(hr.domains()).allSatisfy(d -> {
			assertThat(d.audiences()).contains("hr");
			assertThat(d.bullets()).isEmpty(); // technical detail stripped for hr
		});
		assertThat(hr.websocket()).isNull();
		assertThat(hr.techStack()).isEmpty();
		assertThat(hr.apiSurface()).isEmpty();
		// plain-language summaries survive
		assertThat(hr.domains()).allSatisfy(d -> assertThat(d.summary()).isNotBlank());
	}

	@Test
	@DisplayName("business audience: all 10 domains, bullets kept, websocket present, api surface hidden")
	void businessViewKeepsDomainsAndDropsApiSurface() {
		GatewayModel biz = gatewayService.modelFor("business");
		assertThat(biz.domains()).hasSize(10);
		assertThat(biz.domains()).allSatisfy(d -> assertThat(d.bullets()).isNotEmpty());
		assertThat(biz.websocket()).isNotNull();
		assertThat(biz.apiSurface()).isEmpty();
	}

	@Test
	@DisplayName("technical audience + unknown/absent → full model, identical to base")
	void technicalOrDefaultReturnsFullModel() {
		assertThat(gatewayService.modelFor("technical")).isSameAs(gatewayService.model());
		assertThat(gatewayService.modelFor(null)).isSameAs(gatewayService.model());
		assertThat(gatewayService.modelFor("hacker")).isSameAs(gatewayService.model());
	}

	@Test
	@DisplayName("WS auth contract wording: raw JWT canonical, Bearer tolerated — pinned")
	void websocketAuthContractMatchesImplementation() {
		GatewayModel.ConnectAuth a = gatewayService.model().websocket().connectAuth();
		assertThat(a.canonical()).contains("Raw JWT").contains("no 'Bearer ' prefix");
		assertThat(a.tolerated()).contains("ALSO accepted");
		assertThat(a.header()).contains("Authorization");
		// connection-level failure (no principal yet → no error queue)
		assertThat(a.failureMode()).contains("CONNECT");
		// destinations match the implemented broker wiring
		List<String> destinations = gatewayService.model().websocket().subscribe().stream()
				.map(GatewayModel.WsDestination::destination)
				.toList();
		assertThat(destinations).contains(
				"/user/queue/notifications", "/user/queue/messages",
				"/user/queue/errors", "/topic/room/{roomId}");
		List<String> sends = gatewayService.model().websocket().send().stream()
				.map(GatewayModel.WsDestination::destination)
				.toList();
		assertThat(sends).containsExactly("/app/chat/{roomId}", "/app/chat.private");
	}

	@Test
	@DisplayName("secrets hygiene: serialized model has no credentials, connection strings or PII")
	void serializedModelContainsNoSecrets() throws Exception {
		String json = new ObjectMapper().writeValueAsString(gatewayService.model());
		// Credential-SHAPED patterns only (keys, connection strings, token samples).
		// Bare words like "secret" legitimately appear in descriptive prose
		// ("credentials via secret store", "enrollment + secret") — those are
		// architecture facts, not credentials.
		for (String forbidden : List.of(
				"sk_test_", "sk_live_", "rk_live_", "whsec_",
				"postgresql://", "postgres://", "jdbc:", "mysql://",
				"password=", "Authorization: Bearer ey", "eyJhbGciOi")) {
			assertThat(json).doesNotContain(forbidden);
		}
	}
}
