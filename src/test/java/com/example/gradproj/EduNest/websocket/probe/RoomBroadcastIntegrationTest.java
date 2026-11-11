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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P2-05 / EB-39 & EB-55 — room edit/delete broadcast probes (CHAT-06, payload 4).
 *
 * <ol>
 *   1. A message sent over the socket by a room member is broadcast ON the
 *      room topic to subscribed members.
 *   2. REST {@code PATCH /api/v1/chat-room/{roomId}/messages/{messageId}?content=}
 *      by the same sender broadcasts the UPDATED ChatMessageResponse to
 *      {@code /topic/room/{serverDerivedRoomId}}.
 *   3. REST {@code DELETE ...} broadcasts the {@code {type:"DELETE",
 *      messageId:...}} event shape to the room topic.
 *   4. EB-55 / CHAT-06: Mismatched roomId in path vs message.chatRoom is rejected
 *      with 400 ERR_BUSINESS_RULE ("Message does not belong to this room").
 *   5. EB-55 / CHAT-06: Edit/delete by non-sender is rejected with 403 Forbidden.
 * </ol>
 */
@Tag("critical")
class RoomBroadcastIntegrationTest extends WsStompIntegrationTestBase {

	private static final String MEMBER_EMAIL = "it-eb39-broadcast-member@example.org";
	private static final String MENTOR_EMAIL = "it-eb39-broadcast-mentor@example.org";

	private static final String SENDER_EMAIL = "it-eb55-sender@example.org";
	private static final String OTHER_MEMBER_EMAIL = "it-eb55-other@example.org";
	private static final String MENTOR2_EMAIL = "it-eb55-mentor2@example.org";

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
		Object rawMsgId = created.get("id");
		String messageId = rawMsgId.toString();

		// 2) REST edit by the same sender → updated ChatMessageResponse broadcast
		rest.perform(MockMvcRequestBuilders
				.patch("/api/v1/chat-room/" + ctx.roomPublicId() + "/messages/" + messageId)
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
				.delete("/api/v1/chat-room/" + ctx.roomPublicId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT)))
			.andExpect(MockMvcResultMatchers.status().isOk());

		Map<String, Object> event = member.awaitFrame(
				f -> "DELETE".equals(f.get("type")), 10);
		String deletedId = event.get("messageId").toString();
		if (!deletedId.equals(messageId)) {
			throw new AssertionError("DELETE event references wrong id: " + event);
		}

		// persistence: row really gone
		TimeUnit.SECONDS.sleep(1);
		var rows = messageRepo.findMessagesByRoom(ctx.roomId(), null, PageRequest.of(0, 50));
		if (rows.stream().anyMatch(m -> m.getMessageId().toString().equals(messageId))) {
			throw new AssertionError("Deleted message still persisted — sender-only delete broken");
		}
	}

	@Test
	@DisplayName("EB55/CHAT-06: REST edit with mismatched roomId path variable is rejected with 400 Bad Request")
	void editMessage_mismatchedRoomId_rejectedWith400() throws Exception {
		var ctxA = seedRoom("mismatchA", MENTOR_EMAIL);
		var ctxB = seedRoom("mismatchB", MENTOR2_EMAIL);
		enroll(ctxA.mentorshipId(), SENDER_EMAIL);
		joinRoom(ctxA.roomId(), SENDER_EMAIL);

		TrackingConnection sender = connect(auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT));
		sender.session().subscribe("/topic/room/" + ctxA.roomId(), sender.handler());

		sender.session().send("/app/chat/" + ctxA.roomId(), Map.of("content", "msg in room A"));
		Map<String, Object> created = sender.awaitFrame(
				f -> "msg in room A".equals(f.get("message")), 10);
		Object rawMsgId = created.get("id");
		String messageId = rawMsgId.toString();

		// Attempt to edit messageId belonging to room A using room B path variable
		rest.perform(MockMvcRequestBuilders
				.patch("/api/v1/chat-room/" + ctxB.roomPublicId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT))
				.queryParam("content", "spoofed edit"))
			.andExpect(MockMvcResultMatchers.status().isBadRequest())
			.andExpect(MockMvcResultMatchers.jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Message does not belong to this room")));

		// Content in DB should remain unchanged
		var msg = messageRepo.findByPublicId(UUID.fromString(messageId)).orElseThrow();
		if (!"msg in room A".equals(msg.getContent())) {
			throw new AssertionError("Message content was modified despite room mismatch");
		}
	}

	@Test
	@DisplayName("EB55/CHAT-06: REST delete with mismatched roomId path variable is rejected with 400 Bad Request")
	void deleteMessage_mismatchedRoomId_rejectedWith400() throws Exception {
		var ctxA = seedRoom("delMismatchA", MENTOR_EMAIL);
		var ctxB = seedRoom("delMismatchB", MENTOR2_EMAIL);
		enroll(ctxA.mentorshipId(), SENDER_EMAIL);
		joinRoom(ctxA.roomId(), SENDER_EMAIL);

		TrackingConnection sender = connect(auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT));
		sender.session().subscribe("/topic/room/" + ctxA.roomId(), sender.handler());

		sender.session().send("/app/chat/" + ctxA.roomId(), Map.of("content", "stay alive in room A"));
		Map<String, Object> created = sender.awaitFrame(
				f -> "stay alive in room A".equals(f.get("message")), 10);
		Object rawMsgId = created.get("id");
		String messageId = rawMsgId.toString();

		// Attempt to delete messageId belonging to room A using room B path variable
		rest.perform(MockMvcRequestBuilders
				.delete("/api/v1/chat-room/" + ctxB.roomPublicId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT)))
			.andExpect(MockMvcResultMatchers.status().isBadRequest())
			.andExpect(MockMvcResultMatchers.jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Message does not belong to this room")));

		// Message in DB should still exist
		if (!messageRepo.existsByPublicId(UUID.fromString(messageId))) {
			throw new AssertionError("Message was deleted despite room mismatch");
		}
	}

	@Test
	@DisplayName("EB55/CHAT-06: Non-sender attempting REST edit or delete is rejected with 400 Bad Request")
	void editAndDeleteMessage_nonSender_rejectedWith400() throws Exception {
		var ctx = seedRoom("authGuard", MENTOR_EMAIL);
		enroll(ctx.mentorshipId(), SENDER_EMAIL);
		joinRoom(ctx.roomId(), SENDER_EMAIL);
		enroll(ctx.mentorshipId(), OTHER_MEMBER_EMAIL);
		joinRoom(ctx.roomId(), OTHER_MEMBER_EMAIL);

		TrackingConnection sender = connect(auth.authToken(SENDER_EMAIL, AuthFixture.STUDENT));
		sender.session().subscribe("/topic/room/" + ctx.roomId(), sender.handler());

		sender.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "sender own message"));
		Map<String, Object> created = sender.awaitFrame(
				f -> "sender own message".equals(f.get("message")), 10);
		Object rawMsgId = created.get("id");
		String messageId = rawMsgId.toString();

		// 1) Other member attempts edit -> 400 Bad Request
		rest.perform(MockMvcRequestBuilders
				.patch("/api/v1/chat-room/" + ctx.roomPublicId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(OTHER_MEMBER_EMAIL, AuthFixture.STUDENT))
				.queryParam("content", "hacked edit"))
			.andExpect(MockMvcResultMatchers.status().isBadRequest())
			.andExpect(MockMvcResultMatchers.jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Not allowed to edit this message")));

		// 2) Other member attempts delete -> 400 Bad Request
		rest.perform(MockMvcRequestBuilders
				.delete("/api/v1/chat-room/" + ctx.roomPublicId() + "/messages/" + messageId)
				.header("Authorization", auth.authToken(OTHER_MEMBER_EMAIL, AuthFixture.STUDENT)))
			.andExpect(MockMvcResultMatchers.status().isBadRequest())
			.andExpect(MockMvcResultMatchers.jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Not allowed to delete this message")));

		// DB message unchanged
		var msg = messageRepo.findByPublicId(UUID.fromString(messageId)).orElseThrow();
		if (!"sender own message".equals(msg.getContent())) {
			throw new AssertionError("Message was tampered by unauthorized user");
		}
	}
}
