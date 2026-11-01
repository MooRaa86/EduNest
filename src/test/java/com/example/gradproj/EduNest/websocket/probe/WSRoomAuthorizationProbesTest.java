package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * P1-12 / EB-19 — WS room-authorization probes (WS-01 + WS-02).
 *
 * Contract pinned here (D-14 semantics, user-approved 2026-10-31):
 * <ol>
 *   <li>WRITE on a room = <b>explicit ChatRoomMember row</b> or the room's
 *       <b>owning mentor</b> — an enrolled student who never joined the room is
 *       REJECTED on send.</li>
 *   <li>READ on a room = enrollment proxy (enrolled student or owning mentor)
 *       — an enrolled-but-not-joined student may still SUBSCRIBE.</li>
 *   <li>Foreign users (neither enrolled nor mentors) are rejected on both
 *       SUBSCRIBE and SEND, and nothing persists. Post-EB-39 contract: the
 *       violated frame is DROPPED (no synchronous exception on the async
 *       inbound channel) and the authored message ("Forbidden: you are not a
 *       member of this room") is pushed to the violator's
 *       {@code /user/queue/errors} — the same queue
 *       {@code ChatMessageService} and {@code WebSocketExceptionHandler} use.
 *       The wire-level shape is asserted by the EB-39 socket matrix
 *       ({@code WSAuthMatrixIntegrationTest}).</li>
 *   <li>Defense in depth: {@code ChatMessageService#saveMessage} re-checks the
 *       WRITE rule; a direct call from an unauthorized principal drops the
 *       message with no DB row.</li>
 *   <li>CONNECT still requires a valid raw JWT; missing/invalid tokens are
 *       rejected.</li>
 *   <li>{@code /app/chat.private} rejects self-sends (pair validation).</li>
 * </ol>
 *
 * Note: frames are driven through the real {@code clientInboundChannel}, so
 * the {@code JwtChannelInterceptor} rewrite runs exactly as in production.
 */
@Tag("critical")
class WSRoomAuthorizationProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository chatRooms;
	@Autowired private ChatMessageRepository messageRepo;
	@Autowired private MembersRepo membersRepo;
	@Autowired private ChatMessageService chatMessageService;
	@Autowired private ConversationService conversationService;
	@Qualifier("clientInboundChannel")
	@Autowired private MessageChannel clientInboundChannel;
	@MockitoSpyBean private SimpMessagingTemplate messagingTemplate;

	private static final String MEMBER_EMAIL = "probe-eb19-member@example.org";
	private static final String UNJOINED_EMAIL = "probe-eb19-unjoined@example.org";
	private static final String FOREIGN_EMAIL = "probe-eb19-foreign@example.org";
	private static final String MENTOR_EMAIL = "probe-eb19-mentor@example.org";
	private static final String NAMES = "Probe EB19";

	/* ===================== probes ===================== */

	@Test
	@DisplayName("EB19/[1] member (enrolled + joined) SEND → persists")
	void send_byJoinedMember_persists() throws Exception {
		var ctx = seedRoom("joined-send");
		Long roomId = ctx.roomId();
		auth.enroll(ctx.mentorshipId(), MEMBER_EMAIL);
		joinRoom(roomId, MEMBER_EMAIL);

		String syncError = sendFrame(roomId, "{\"content\":\"member writes to his room\"}", MEMBER_EMAIL);

		if (syncError != null) {
			throw new AssertionError("Joined member's SEND failed synchronously: " + syncError);
		}
		String persisted = awaitPersistedContent(roomId);
		if (!"member writes to his room".equals(persisted)) {
			throw new AssertionError("Expected the member's message to persist, got: " + persisted);
		}
	}

	@Test
	@DisplayName("EB19/[2] enrolled-but-not-joined SEND → rejected, nothing persists")
	void send_byEnrolledNotJoined_rejected() throws Exception {
		var ctx = seedRoom("unjoined-send");
		auth.enroll(ctx.mentorshipId(), UNJOINED_EMAIL);

		String syncError = sendFrame(ctx.roomId(), "{\"content\":\"sneaky write\"}", UNJOINED_EMAIL);

		assertSendRejected(ctx.roomId(), syncError, UNJOINED_EMAIL);
	}

	@Test
	@DisplayName("EB19/[3] foreign user SEND → rejected, nothing persists")
	void send_byForeignUser_rejected() throws Exception {
		var ctx = seedRoom("foreign-send");
		auth.student(FOREIGN_EMAIL); // real user, no enrollment, no membership

		String syncError = sendFrame(ctx.roomId(), "{\"content\":\"foreign write\"}", FOREIGN_EMAIL);

		assertSendRejected(ctx.roomId(), syncError, FOREIGN_EMAIL);
	}

	@Test
	@DisplayName("EB19/[4] owning mentor (not joined) SEND → persists (mentor-owner write rule)")
	void send_byOwningMentor_persists() throws Exception {
		var ctx = seedRoom("mentor-send");
		Long roomId = ctx.roomId();

		String syncError = sendFrame(roomId, "{\"content\":\"owner mentor writes\"}", ctx.mentorEmail());

		if (syncError != null) {
			throw new AssertionError("Owning mentor's SEND failed synchronously: " + syncError);
		}
		String persisted = awaitPersistedContent(roomId);
		if (!"owner mentor writes".equals(persisted)) {
			throw new AssertionError("Expected the mentor's message to persist, got: " + persisted);
		}
	}

	@Test
	@DisplayName("EB19/[5] member SUBSCRIBE → allowed")
	void subscribe_byEnrolledMember_allowed() {
		var ctx = seedRoom("member-sub");
		auth.enroll(ctx.mentorshipId(), MEMBER_EMAIL);

		String syncError = sendSubscribe(ctx.roomId(), MEMBER_EMAIL);

		if (syncError != null) {
			throw new AssertionError("Enrolled member could not SUBSCRIBE: " + syncError);
		}
	}

	@Test
	@DisplayName("EB19/[6] foreign user SUBSCRIBE → dropped + authored error pushed to /user/queue/errors (eavesdrop blocked)")
	void subscribe_byForeignUser_rejected() {
		var ctx = seedRoom("foreign-sub");
		auth.student(FOREIGN_EMAIL);

		String syncError = sendSubscribe(ctx.roomId(), FOREIGN_EMAIL);

		if (syncError != null) {
			throw new AssertionError("Foreign SUBSCRIBE threw synchronously — post-EB-39 the violated frame is "
					+ "dropped silently, got: " + syncError);
		}
		// the accounting must still happen: authored error on the violator's queue
		verify(messagingTemplate).convertAndSendToUser(eq(FOREIGN_EMAIL), eq("/queue/errors"),
				eq("Forbidden: you are not a member of this room"));
	}

	@Test
	@DisplayName("EB19/[7] defense in depth: saveMessage called directly by unauthorized principal → dropped, no row")
	void saveMessage_directCall_unauthorized_dropped() {
		var ctx = seedRoom("direct-unauth");
		auth.student(FOREIGN_EMAIL);

		var response = chatMessageService.saveMessage(
				ctx.roomId(), "bypass attempt", new ChatPrincipal(FOREIGN_EMAIL, NAMES));

		if (response != null) {
			throw new AssertionError("saveMessage returned a response for an unauthorized sender — defense net missing");
		}
		var rows = messageRepo.findMessagesByRoom(ctx.roomId(), null, PageRequest.of(0, 1));
		if (!rows.isEmpty()) {
			throw new AssertionError("Unauthorized direct saveMessage persisted a row — defense net missing");
		}
	}

	@Test
	@DisplayName("EB19/[8] CONNECT without Authorization header → rejected")
	void connect_withoutToken_rejected() {
		StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
		acc.setSessionId("eb19-probe-connect");
		acc.setSessionAttributes(new HashMap<>());

		Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());

		try {
			clientInboundChannel.send(frame);
			throw new AssertionError("CONNECT without a token was accepted — CONNECT auth broken");
		} catch (Exception expected) {
			// contract: missing JWT → rejected
		}
	}

	@Test
	@DisplayName("EB19/[9] CONNECT with invalid JWT → rejected")
	void connect_withGarbageToken_rejected() {
		StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
		acc.setNativeHeader("Authorization", "not-a-real-jwt");
		acc.setSessionId("eb19-probe-connect-bad");
		acc.setSessionAttributes(new HashMap<>());

		Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());

		try {
			clientInboundChannel.send(frame);
			throw new AssertionError("CONNECT with a garbage token was accepted — CONNECT auth broken");
		} catch (Exception expected) {
			// contract: invalid JWT → rejected
		}
	}

	@Test
	@DisplayName("EB19/[10] /app/chat.private self-send → rejected (pair validation)")
	void privateMessage_selfSend_rejected() {
		var ctx = seedRoom("private-self");
		auth.student(FOREIGN_EMAIL);

		ConversationMessageRequest request = ConversationMessageRequest.builder()
				.recipientEmail(FOREIGN_EMAIL)
				.content("talking to myself")
				.build();

		try {
			conversationService.sendMessage(FOREIGN_EMAIL, request);
			throw new AssertionError("Self-send was accepted — private pair validation missing");
		} catch (globalLogicEx expected) {
			// contract: sender≠recipient
		}
	}

	/* ===================== helpers ===================== */

	private record RoomCtx(Long roomId, Long mentorshipId, String mentorEmail) {
	}

	private RoomCtx seedRoom(String label) {
		var chain = auth.mentorship(MENTOR_EMAIL + label, Status.ACTIVE);
		ChatRoom room = chatRooms.saveAndFlush(ChatRoom.builder()
				.name("probe eb19 " + label)
				.mentorship(chain.mentorship())
				.creator(chain.mentor())
				.build());
		return new RoomCtx(room.getId(), chain.mentorship().getId(), MENTOR_EMAIL + label);
	}

	private void joinRoom(Long roomId, String email) {
		var room = chatRooms.findRoomById(roomId).orElseThrow();
		var user = auth.student(email);
		membersRepo.saveAndFlush(ChatRoomMember.builder()
				.chatRoom(room)
				.user(user)
				.joinedAt(LocalDateTime.now())
				.build());
	}

	private String sendFrame(Long roomId, String body, String principalEmail) {
		StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
		acc.setDestination("/app/chat/" + roomId);
		acc.setUser(new ChatPrincipal(principalEmail, NAMES));
		acc.setSessionId("eb19-probe");
		acc.setSessionAttributes(new HashMap<>());

		Message<byte[]> frame = MessageBuilder.createMessage(
				body.getBytes(StandardCharsets.UTF_8), acc.getMessageHeaders());

		try {
			clientInboundChannel.send(frame);
		} catch (Exception e) {
			return e.getClass().getName() + ": " + e.getMessage();
		}
		return null;
	}

	private String sendSubscribe(Long roomId, String principalEmail) {
		StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		acc.setDestination("/topic/room/" + roomId);
		acc.setUser(new ChatPrincipal(principalEmail, NAMES));
		acc.setSessionId("eb19-probe-sub");
		acc.setSessionAttributes(new HashMap<>());

		Message<byte[]> frame = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());

		try {
			clientInboundChannel.send(frame);
		} catch (Exception e) {
			return e.getClass().getName() + ": " + e.getMessage();
		}
		return null;
	}

	private void assertSendRejected(Long roomId, String syncError, String principalEmail)
			throws InterruptedException {
		String persisted = awaitPersistedContent(roomId);
		if (syncError != null) {
			throw new AssertionError("Violated SEND threw synchronously — post-EB-39 the frame must be "
					+ "dropped silently, got: " + syncError);
		}
		if (persisted != null) {
			throw new AssertionError("Unauthorized SEND persisted into room " + roomId
					+ " — WS-01 NOT fixed (frame not dropped and a row landed)");
		}
		// the accounting must still happen: authored error on the violator's queue
		verify(messagingTemplate).convertAndSendToUser(eq(principalEmail), eq("/queue/errors"),
				eq("Forbidden: you are not a member of this room"));
	}

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
}
