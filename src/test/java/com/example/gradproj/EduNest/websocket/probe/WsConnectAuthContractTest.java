package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.WsStompIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [EB-112][P5-06] Pins the DOCUMENTED WebSocket CONNECT authentication
 * contract against the real implementation — the fact the root-gateway
 * showcase (GatewayService#websocketContract) presents to the FE team.
 *
 * Verified truth ( JwtChannelInterceptor → JwtService#isTokenValid ):
 * <ul>
 *   <li><b>Raw JWT (no "Bearer " prefix)</b> is the canonical FE contract —
 *       this is what WsStompIntegrationTestBase always sends and what
 *       mds/websocket/MDS.md §6 documents.</li>
 *   <li><b>"Bearer " + JWT is ALSO accepted</b>: isTokenValid strips the
 *       prefix internally (JwtService lines ~152-156) before parsing.</li>
 *   <li><b>Missing/invalid token fails the CONNECT itself</b> — a
 *       connection-level rejection (no principal exists yet, so no error
 *       queue can explain it). On the wire: the connect future throws.</li>
 * </ul>
 * The showcase wording must never drift from this test.
 */
@Tag("gateway")
@Tag("websocket")
class WsConnectAuthContractTest extends WsStompIntegrationTestBase {

	@Test
	@DisplayName("CONNECT with the raw JWT (canonical FE contract) succeeds")
	void rawJwtConnectSucceeds() throws Exception {
		String email = "ws-auth-raw-" + System.nanoTime() + "@probe.test";
		var user = auth.student(email);
		assertThat(user).isNotNull();

		String jwt = auth.authToken(email, AuthFixture.STUDENT);
		var connection = connect(jwt); // RAW — no prefix
		assertThat(connection.session().isConnected()).isTrue();
	}

	@Test
	@DisplayName("CONNECT with 'Bearer <JWT>' ALSO succeeds (prefix stripped server-side)")
	void bearerPrefixedConnectAlsoSucceeds() throws Exception {
		String email = "ws-auth-bearer-" + System.nanoTime() + "@probe.test";
		var user = auth.student(email);
		assertThat(user).isNotNull();

		String jwt = auth.authToken(email, AuthFixture.STUDENT);
		var connection = connect("Bearer " + jwt);
		assertThat(connection.session().isConnected()).isTrue();
	}

	@Test
	@DisplayName("CONNECT with an invalid token fails at connection level (future throws)")
	void invalidTokenConnectIsRejected() throws Exception {
		assertThatThrownBy(() -> connect("not-a-real-jwt"))
				.isInstanceOf(ExecutionException.class);
	}
}
