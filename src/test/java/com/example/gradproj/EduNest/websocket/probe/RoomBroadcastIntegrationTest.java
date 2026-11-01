package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.WsStompIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * P2-05 / EB-39 — room edit/delete broadcast probes (CHAT-06, payload 4).
 *
 * Pins TODAY's real behavior (pre EB-20 baseline, deliberately established
 * before the server-derived routing rework of P1-13):
 * <ol>
 *   1. A message sent over the socket by a room member is broadcast ON the
 *      room topic to subscribed members.
 *   2. REST {@code PATCH /api/v1/chat-room/{roomId}/messages/{messageId}?content=}
 *      by the same sender broadcasts the UPDATED ChatMessageResponse to
 *      {@code /topic/room/{pathVar}}.
 *   3. REST {@code DELETE ...} broadcasts the {@code {type:"DELETE",
 *      messageId:...}} event shape to the room topic.
 * </ol>
 * Known CHAT-06 shadow (documented, NOT asserted here): the controller builds
 * the topic from the PATH VARIABLE, not the message's actual room — a wrong
 * {roomId} would broadcast into a different room's topic. EB-20 re-derives the
 * routing server-side; when it lands, that becomes a negative probe.
 */
@Tag("critical")
class RoomBroadcastIntegrationTest extends WsStompIntegrationTestBase {

	private static final String MEMBER_EMAIL = "it-eb39-broadcast-member@example.org";
	private static final String MENTOR_EMAIL = "it-eb39-broadcast-mentor@example.org";

	@Test
	@DisplayName("EB39/CAST[1] socket SEND → live broadcast to subscribed peer; REST edit → updated broadcast; REST delete → DELETE event")
	void editAndDeleteBroadcastToRoomTopic() throws Exception {
		var ctx = seedRoom("cast", MENTOR_EMAIL);
		enroll(ctx.mentorshipId(), MEMBER_EMAIL);
		joinRoom(ctx.roomId(), MEMBER_EMAIL);

		TrackingConnection member = connect(auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT));
		member.session().subscribe("/topic/room/" + ctx.roomId(), member.handler());

		// 1) sender posts over the socket
		member.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "original text"));

		Map<String, Object> created = member.awaitFrame(
				f -> "original text".equals(f.get("message")), 10);
		Integer messageId = ((Number) created.get("id")).intValue();

		// 2) REST edit by the same sender → updated ChatMessageResponse broadcast
		rest.perform(MockMvcRequestBuilders
				.patch("/api/v1/chat-room/" + ctx.roomId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT))
				.queryParam("content", "edited text"))
			.andExpect(MockMvcResultMatchers.status().isOk());

		Map<String, Object> edited = member.awaitFrame(
				f -> "edited text".equals(f.get("message")), 10);
		if (!"edited text".equals(edited.get("message"))) {
			throw new AssertionError("Edit broadcast lost: " + edited);
		}

		// 3) REST delete by the same sender → {type: DELETE, messageId} event
		rest.perform(MockMvcRequestBuilders
				.delete("/api/v1/chat-room/" + ctx.roomId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT)))
			.andExpect(MockMvcResultMatchers.status().isOk());

		Map<String, Object> event = member.awaitFrame(
				f -> "DELETE".equals(f.get("type")), 10);
		int deletedId = ((Number) event.get("messageId")).intValue();
		if (deletedId != messageId) {
			throw new AssertionError("DELETE event references wrong id: " + event);
		}

		// persistence: row really gone
		TimeUnit.SECONDS.sleep(1);
		var rows = messageRepo.findMessagesByRoom(ctx.roomId(), null, PageRequest.of(0, 50));
		if (rows.stream().anyMatch(m -> m.getMessageId() == messageId.longValue())) {
			throw new AssertionError("Deleted message still persisted — sender-only delete broken");
		}
	}
}
