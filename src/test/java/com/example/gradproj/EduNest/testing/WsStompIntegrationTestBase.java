package com.example.gradproj.EduNest.testing;

import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * P2-05 / EB-39 — socket-level WebSocket integration test base.
 *
 * Boots the REAL application on a random local port (same
 * {@code {"prod","test"}} profile doctrine as
 * {@link ProdChainIntegrationTestBase}: prod first so the security chain sees
 * "prod"; test last so every H2/stub override wins), then drives real STOMP
 * frames over true WebSocket sessions through {@code /ws/websocket} (the raw
 * transport exposed alongside the SockJS wiring) using
 * {@link WebSocketStompClient}. The same context also wires {@link MockMvc},
 * so the REST companions (room edit/delete broadcasts) are probed without a
 * second context — one shared context for all suites below.
 *
 * This is the "WS / async non-transactional test base" deferred to P2-05 by the
 * P2-02 task definition. It is non-transactional by design: every probe owns
 * its own seeding and asserts through the real broker/persistence paths,
 * exactly as a frontend client would experience them.
 *
 * Error contract (P1-12 / EB-19, repaired by P2-05 / EB-39): authorization
 * violations are NOT surfaceable as STOMP ERROR frames — the async
 * clientInboundChannel swallows interceptor exceptions and nothing reaches the
 * wire (proven by this suite's socket matrix). Instead the violated frame is
 * DROPPED and the authored message ("Forbidden: you are not a member of this
 * room") is pushed to the violating session's {@code /user/queue/errors}; a
 * failing CONNECT still fails the session setup (connection-level rejection,
 * no principal exists yet). Both shapes are asserted from the wire in the
 * suites built on this base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
public abstract class WsStompIntegrationTestBase {

	private static final Path UPLOAD_ROOT = createUploadRoot();

	private static Path createUploadRoot() {
		try {
			return Files.createTempDirectory("edunest-ws-it-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void uploadRoot(DynamicPropertyRegistry registry) {
		registry.add("app.upload.base-dir", () -> UPLOAD_ROOT.toString());
	}

	@Autowired
	protected AuthFixture auth;
	@Autowired
	protected ChatRoomRepository chatRooms;
	@Autowired
	protected ChatMessageRepository messageRepo;
	@Autowired
	protected MembersRepo membersRepo;
	@Autowired
	protected MockMvc rest;

	@LocalServerPort
	private int serverPort;

	private WebSocketStompClient stompClient;
	private ThreadPoolTaskScheduler heartbeatScheduler;
	private final List<StompSession> openSessions = new ArrayList<>();

	@BeforeEach
	void initWsClient() {
		if (stompClient != null) return;
		heartbeatScheduler = new ThreadPoolTaskScheduler();
		heartbeatScheduler.setThreadNamePrefix("ws-it-heartbeat-");
		heartbeatScheduler.initialize();
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new MappingJackson2MessageConverter());
		stompClient.setTaskScheduler(heartbeatScheduler);
	}

	protected int port() {
		return serverPort;
	}

	/**
	 * Connection-level tracker: captures ERROR payloads + delivered frames.
	 * Topic frames land in {@code topicFrames}, user-queue frames in
	 * {@code userQueueFrames}; frames deserialize as generic maps so the
	 * suites inspect payload keys ("message"/"id" vs "type"/"messageId").
	 */
	public static class TrackingHandler extends StompSessionHandlerAdapter {

		public final List<String> errorPayloads = new CopyOnWriteArrayList<>();
		public final List<Map<String, Object>> topicFrames = new CopyOnWriteArrayList<>();
		public final List<Map<String, Object>> userQueueFrames = new CopyOnWriteArrayList<>();

		public List<String> errorPayloads() { return errorPayloads; }
		public List<Map<String, Object>> topicFrames() { return topicFrames; }
		public List<Map<String, Object>> userQueueFrames() { return userQueueFrames; }

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return Map.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			if (payload instanceof Map<?, ?> map) {
				@SuppressWarnings({"unchecked", "rawtypes"})
				Map<String, Object> casted = (Map) map;
				String dest = headers.getDestination();
				if (dest != null && dest.startsWith("/user/")) {
					userQueueFrames.add(casted);
				} else {
					topicFrames.add(casted);
				}
			}
		}

		@Override
		public void handleException(StompSession session, StompCommand command,
				StompHeaders headers, byte[] payload, Throwable exception) {
			if (payload != null && payload.length > 0) {
				errorPayloads.add(new String(payload, StandardCharsets.UTF_8));
			} else if (exception != null) {
				errorPayloads.add(exception.getMessage());
			}
		}
	}

	/** One opened STOMP session + its tracker, with bounded awaits. */
	public record TrackingConnection(StompSession session, TrackingHandler handler) {

		public Map<String, Object> awaitFrame(java.util.function.Predicate<Map<String, Object>> match,
				long seconds)
				throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			while (System.nanoTime() < deadline) {
				for (Map<String, Object> frame : handler().topicFrames()) {
					if (match.test(frame)) return frame;
				}
				TimeUnit.MILLISECONDS.sleep(100);
			}
			throw new AssertionError("No matching topic frame within " + seconds + "s");
		}

		public Map<String, Object> awaitUserQueueFrame(
				java.util.function.Predicate<Map<String, Object>> match, long seconds)
				throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			while (System.nanoTime() < deadline) {
				for (Map<String, Object> frame : handler().userQueueFrames()) {
					if (match.test(frame)) return frame;
				}
				TimeUnit.MILLISECONDS.sleep(100);
			}
			throw new AssertionError("No matching /user/queue frame within " + seconds + "s");
		}

		public String awaitError(long seconds) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			while (System.nanoTime() < deadline) {
				if (!handler().errorPayloads().isEmpty()) {
					return handler().errorPayloads().get(0);
				}
				TimeUnit.MILLISECONDS.sleep(100);
			}
			throw new AssertionError("No ERROR frame within " + seconds + "s");
		}

		/**
		 * Auth-error contract (P1-12 / EB-12): /queue/errors payload is a
		 * PLAIN STRING (same shape as the WebSocketExceptionHandler and
		 * ChatMessageService pushes). Jackson cannot bind a plain JSON string
		 * into Map, so such frames surface through
		 * {@link TrackingHandler#handleException} (errorPayloads) rather than
		 * handleFrame — this helper awaits the authored text on either path.
		 */
		public String awaitUserError(String contains, long seconds) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
			while (System.nanoTime() < deadline) {
				for (String e : handler().errorPayloads()) {
					if (e != null && e.contains(contains)) return e;
				}
				for (Map<String, Object> f : handler().userQueueFrames()) {
					Object m = f.get("message");
					if (m instanceof String s && s.contains(contains)) return s;
				}
				TimeUnit.MILLISECONDS.sleep(100);
			}
			throw new AssertionError("No /queue/errors frame containing \"" + contains
					+ "\" within " + seconds + "s");
		}

		public void close() {
			try {
				session.disconnect();
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Opens a real socket-level session at {@code /ws/websocket} with the raw
	 * JWT (no "Bearer " prefix, same as the FE contract) as the CONNECT native
	 * {@code Authorization} header. {@code null} token connects WITHOUT the
	 * header. A server-side CONNECT rejection surfaces as a thrown exception
	 * from the connect future — the connection-level contract.
	 */
	protected TrackingConnection connect(String rawToken) throws Exception {
		TrackingHandler handler = new TrackingHandler();
		StompHeaders headers = new StompHeaders();
		headers.setHeartbeat(new long[]{20_000, 20_000});
		if (rawToken != null) {
			headers.add("Authorization", rawToken);
		}
		URI uri = URI.create("ws://localhost:" + serverPort + "/ws/websocket");
		StompSession session = stompClient.connectAsync(uri, new WebSocketHttpHeaders(),
				headers, handler).get(15, TimeUnit.SECONDS);
		openSessions.add(session);
		return new TrackingConnection(session, handler);
	}

	/** Real-JWT minting + seeding helpers (AuthFixture contract). */
	protected record RoomCtx(Long roomId, Long mentorshipId, String mentorEmail) {
	}

	protected RoomCtx seedRoom(String label, String mentorEmail) {
		var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
		ChatRoom room = chatRooms.saveAndFlush(ChatRoom.builder()
				.name("probe eb39 " + label)
				.mentorship(chain.mentorship())
				.creator(chain.mentor())
				.build());
		return new RoomCtx(room.getId(), chain.mentorship().getId(), mentorEmail);
	}

	protected void enroll(Long mentorshipId, String studentEmail) {
		auth.enroll(mentorshipId, studentEmail);
	}

	protected void joinRoom(Long roomId, String studentEmail) {
		var room = chatRooms.findRoomById(roomId).orElseThrow();
		var user = auth.student(studentEmail);
		membersRepo.saveAndFlush(ChatRoomMember.builder()
				.chatRoom(room)
				.user(user)
				.joinedAt(LocalDateTime.now())
				.build());
	}

	/** Bounded poll helper — first non-null result; {@code null} on timeout. */
	protected static <T> T awaitOrNull(Supplier<T> probe, long seconds)
			throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
		while (System.nanoTime() < deadline) {
			T value = probe.get();
			if (value != null) return value;
			TimeUnit.MILLISECONDS.sleep(100);
		}
		return null;
	}

	@AfterEach
	void tearDownSessions() {
		for (StompSession s : openSessions) {
			try {
				if (s.isConnected()) s.disconnect();
			} catch (Exception ignored) {
			}
		}
		openSessions.clear();
	}
}
