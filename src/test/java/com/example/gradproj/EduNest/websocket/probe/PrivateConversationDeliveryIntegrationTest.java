package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.WsStompIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * P2-05 / EB-39 — private conversation delivery over real sockets (payload 4).
 *
 * Pins TODAY's real behavior for the /app/chat.private flow:
 * <ol>
 *   1. Sender posts to /app/chat.private; the RECEIVER receives the message on
 *      their /user/queue/messages user queue (user-destination resolution by
 *      the recipient email).
 *   2. The sender ALSO receives their own copy (two-queue delivery, lines 34–45
 *      of ConversationWebSocketControllers).
 * </ol>
 * Scope note (per blueprint, approved): the CHAT-04 spoof-routing matrix —
 * client-controlled recipientEmail on REST edit/delete pushes — is tested when
 * P1-13 (EB-20) lands the server-derived event routing. Today's suite asserts
 * the /app/chat.private path, which is NOT client-controlled routing: the
 * sender is the authenticated principal and the pair shape is validated by
 * ChatAuthorization#validatePrivatePair (EB-19).
 */
@Tag("critical")
class PrivateConversationDeliveryIntegrationTest extends WsStompIntegrationTestBase {

	private static final String SENDER_EMAIL = "it-eb39-priv-sender@example.org";
	private static final String RECIPIENT_EMAIL = "it-eb39-priv-receiver@example.org";

	@Test
	@DisplayName("EB39/PRIV[1] /app/chat.private → recipient gets /user/queue/messages; sender gets own copy")
	void privateMessageDeliveredToBothQueues() throws Exception {
		auth.student(SENDER_EMAIL);
		auth.student(RECIPIENT_EMAIL);

		TrackingConnection receiver = connect(auth.authToken(RECIPIENT_EMAIL, AuthFixture.STUDENT));
		TrackingConnection sender = connect(auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT));

		receiver.session().subscribe("/user/queue/messages", receiver.handler());
		sender.session().subscribe("/user/queue/messages", sender.handler());

		sender.session().send("/app/chat.private", Map.of(
				"recipientEmail", RECIPIENT_EMAIL,
				"content", "private hello over the wire"));

		Map<String, Object> onReceiver = receiver.awaitUserQueueFrame(
				f -> "private hello over the wire".equals(f.get("content")), 10);
		if (onReceiver == null) {
			throw new AssertionError("Recipient did not receive the private message");
		}

		Map<String, Object> onSender = sender.awaitUserQueueFrame(
				f -> "private hello over the wire".equals(f.get("content")), 10);
		if (onSender == null) {
			throw new AssertionError("Sender did not receive their own copy");
		}
	}
}
