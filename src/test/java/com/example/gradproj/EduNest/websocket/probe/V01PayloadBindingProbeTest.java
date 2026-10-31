package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * P0-03 / EB-1 — V-01: STOMP payload-binding verification probe (CHAT-08).
 *
 * HISTORY (Option A doctrine, flip-in-same-PR):
 *   • FIRST RUN (pre-fix audit, 2026-10-29): the handler bound a raw
 *     {@code String content}; every frame body — plain text, JSON string
 *     literal, JSON object — was persisted VERBATIM. An object payload landed
 *     in the DB as literal JSON text (CHAT-08 mechanism CONFIRMED).
 *   • SAME-PR FIX (Omar's decision, 2026-10-29): the handler now binds
 *     {@code @Payload ChatMessageRequest} (DTO, "content" field), the DB
 *     contract is a pure-text message line, and raw/blank payloads never
 *     persist.
 *
 * CURRENT EXPECTATIONS (post-fix contract, new source of truth):
 *   1. plain-text body        → rejected by the payload converter → NO row.
 *   2. JSON string literal    → rejected by the payload converter → NO row.
 *   3. JSON object {"content": "..."} → deserialized DTO → row persists with
 *      the PURE text (no JSON / no quotes anywhere in the persisted content).
 *   4. JSON object with blank content → service guard → NO row.
 *
 * FE CONTRACT: room-chat SEND bodies must be JSON objects {"content": "..."}
 * — see dto/chat/ChatMessageRequest. Raw-string building from the FE is now a
 * client bug (nothing persists; the error surfaces on the sender's
 * /queue/errors queue via WebSocketExceptionHandler).
 */
@Tag("critical")
class V01PayloadBindingProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository chatRooms;
	@Autowired private ChatMessageRepository messageRepo;
	@Autowired private MembersRepo membersRepo;
	@Qualifier("clientInboundChannel")
	@Autowired private MessageChannel clientInboundChannel;

	private static final String SENDER_EMAIL = "probe-v01-sender@example.org";
	private static final String SENDER_NAME = "Probe V01 Sender";

	private static final String PLAIN_TEXT_BODY = "hello from plain text frame";
	private static final String JSON_STRING_LITERAL_BODY = "\"hello as JSON string literal\"";
	private static final String JSON_OBJECT_BODY = "{\"content\":\"pure text from FE object\"}";

	/* ===================== probes ===================== */

	/**
	 * NEW CONTRACT — a raw unquoted body is not a valid {"content":...} object
	 * → rejected before the handler → nothing persists.
	 */
	@Test
	@DisplayName("V01/matrix[1] FIXED: plain-text body → rejected, nothing persists")
	void v01_plainTextBodyRejected() throws Exception {
		Long roomId = seedRoom("plain");
		String syncError = sendFrame(roomId, PLAIN_TEXT_BODY);
		String persisted = awaitPersistedContent(roomId);

		assertRejected(roomId, persisted, syncError);
	}

	/**
	 * NEW CONTRACT — a quoted JSON string is not a {"content":...} object
	 * → rejected before the handler → nothing persists.
	 */
	@Test
	@DisplayName("V01/matrix[2] FIXED: JSON string-literal body → rejected, nothing persists")
	void v01_jsonStringLiteralRejected() throws Exception {
		Long roomId = seedRoom("jsonstr");
		String syncError = sendFrame(roomId, JSON_STRING_LITERAL_BODY);
		String persisted = awaitPersistedContent(roomId);

		assertRejected(roomId, persisted, syncError);
	}

	/**
	 * THE FIX — the object payload now deserializes into ChatMessageRequest
	 * and the PURE text ("content" value, minus JSON/quotes) is persisted.
	 */
	@Test
	@DisplayName("V01/matrix[3] FIXED: JSON object body → DTO deserialized, PURE text persisted")
	void v01_jsonObjectBodyChatPureText() throws Exception {
		Long roomId = seedRoom("jsonobj");
		String syncError = sendFrame(roomId, JSON_OBJECT_BODY);
		String persisted = awaitPersistedContent(roomId);

		if (syncError != null) {
			throw new AssertionError("Object frame failed synchronously: " + syncError);
		}
		if (persisted == null) {
			throw new AssertionError("No ChatMessage persisted for room " + roomId
					+ " — the DTO binding did not accept the object payload");
		}
		if (!persisted.equals("pure text from FE object")) {
			throw new AssertionError("Persisted content is not the pure text: "
					+ persisted + " — JSON binding leaked into the DB");
		}
	}

	/**
	 * Defense-in-depth — an object that deserializes fine but carries a blank
	 * content must never persist (ChatMessageService guard).
	 */
	@Test
	@DisplayName("V01/matrix[4] FIXED: blank-content object → service guard rejects, nothing persists")
	void v01_blankContentObjectRejected() throws Exception {
		Long roomId = seedRoom("blank");
		String syncError = sendFrame(roomId, "{\"content\":\"   \"}");
		String persisted = awaitPersistedContent(roomId);

		assertRejected(roomId, persisted, syncError);
	}

	/* ===================== helpers ===================== */

	/** Seeds mentor → ACTIVE mentorship → room; the sender is enrolled in the
	 * mentorship AND joined the room, per the P1-12/EB-19 WRITE rule
	 * (previously the sender needed no membership — the guards did not exist). */
	private Long seedRoom(String label) {
		var chain = auth.mentorship("probe-v01-mentor-" + label + "@example.org", Status.ACTIVE);
		auth.student(SENDER_EMAIL);
		ChatRoom room = chatRooms.saveAndFlush(ChatRoom.builder()
				.name("probe v01 " + label)
				.mentorship(chain.mentorship())
				.creator(chain.mentor())
				.build());
		auth.enroll(chain.mentorship().getId(), SENDER_EMAIL);
		membersRepo.saveAndFlush(ChatRoomMember.builder()
				.chatRoom(room)
				.user(auth.student(SENDER_EMAIL))
				.joinedAt(LocalDateTime.now())
				.build());
		return room.getId();
	}

	/**
	 * Sends a SEND frame through the clientInboundChannel with a pre-set user
	 * (the sender is a joined room member — the P1-12/EB-19 WRITE rule is in
	 * force; see seedRoom). Records a synchronous conversion/handler exception
	 * if one propagates to the caller.
	 */
	private String sendFrame(Long roomId, String body) {
		StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
		acc.setDestination("/app/chat/" + roomId);
		acc.setUser(new ChatPrincipal(SENDER_EMAIL, SENDER_NAME));
		acc.setSessionId("v01-probe");
		acc.setSessionAttributes(new HashMap<>());

		byte[] payload = body.getBytes(StandardCharsets.UTF_8);
		Message<byte[]> frame = MessageBuilder.createMessage(payload, acc.getMessageHeaders());

		try {
			clientInboundChannel.send(frame);
		} catch (Exception e) {
			return e.getClass().getName() + ": " + e.getMessage();
		}
		return null;
	}

	/**
	 * The inbound channel may or may not run asynchronously depending on the
	 * configured executor — poll the persisted projection briefly so the test
	 * is deterministic either way.
	 */
	private String awaitPersistedContent(Long roomId) throws InterruptedException {
		for (int i = 0; i < 60; i++) {
			var rows = messageRepo.findMessagesByRoom(roomId, null, PageRequest.of(0, 1));
			if (!rows.isEmpty()) {
				return rows.get(0).getContent();
			}
			TimeUnit.MILLISECONDS.sleep(100);
		}
		return null;
	}

	private void assertRejected(Long roomId, String persisted, String syncError) {
		if (persisted != null) {
			throw new AssertionError("Expected NO persistence under the DTO contract, but room "
					+ roomId + " got content=" + persisted
					+ (syncError != null ? " (sync error: " + syncError + ")" : ""));
		}
	}
}
