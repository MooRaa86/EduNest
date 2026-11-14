package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [EB-112][P5-06] Prod-chain security probes for the gateway routes.
 *
 * Runs on the REAL production security chain (prod profile first — same
 * doctrine as {@code ProdChainIntegrationTestBase}). Pins:
 * <ol>
 *   <li>The gateway is anonymous-public on GET/HEAD for every documented
 *       route ({@code /}, {@code /view}, {@code /diagrams}, {@code /status},
 *       {@code /api/v1/showcase/**}) — a browser hitting the root domain
 *       must never be asked to log in.</li>
 *   <li>The gateway is GET/HEAD-only: non-GET/HEAD methods are denied by the
 *       probe-style method restriction in {@code ProjectSecurityConfig#prodChain}
 *       ({@code PublicRoutesPolicy.PUBLIC_GATEWAY_PATTERNS}).</li>
 * </ol>
 */
@Tag("gateway")
class GatewaySecurityProbeTest extends ProdChainIntegrationTestBase {

	private static final String[] GATEWAY_ROUTES = {
			"/", "/view", "/diagrams", "/status",
			"/api/v1/showcase", "/api/v1/showcase/diagrams", "/api/v1/showcase/status"
	};

	@Test
	@DisplayName("anonymous GET on every gateway route → 200 (no login required)")
	void anonymousGetIsPublicOnAllGatewayRoutes() throws Exception {
		for (String route : GATEWAY_ROUTES) {
			mockMvc.perform(get(route).accept("application/json"))
					.andExpect(status().isOk());
		}
	}

	@Test
	@DisplayName("anonymous HEAD on the gateway root → allowed")
	void anonymousHeadIsAllowed() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.head("/"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("POST on gateway routes → denied (GET/HEAD-only surface)")
	void postIsDeniedOnGatewayRoutes() throws Exception {
		// denyAll + ANONYMOUS → AccessDeniedException → ExceptionTranslationFilter
		// treats the anonymous principal as unauthenticated → the authentication
		// entry point answers 401 (same convention as the other probes: anonymous
		// → 401, authenticated → 403). Either way the request never reaches the
		// controller — the route matrix registers no non-GET/HEAD verbs.
		for (String route : GATEWAY_ROUTES) {
			mockMvc.perform(post(route).content("{}"))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("stale/malformed Authorization header on a gateway route is ignored, not fatal")
	void malformedJwtOnPublicGatewayRouteIsGracefullyIgnored() throws Exception {
		// PublicRoutesPolicy doctrine: the JWT validator ignores invalid tokens
		// on public routes instead of failing fast — verified on the gateway too.
		mockMvc.perform(get("/").header("Authorization", "not-a-real-jwt"))
				.andExpect(status().isOk());
	}
}
