package com.example.gradproj.EduNest.websocket.probe;

import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.WsStompIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * P2-05 / EB-39 — WS AUTH matrix asserted on real sockets (payload 4).
 *
 * Proves at the wire level what the P1-12 (EB-19) channel-interceptor contract
 * is:
 * <ol>
 *   1. CONNECT: valid raw JWT → session; missing header → connection
 *      rejected; garbage token → connection rejected.
 *   2. SUBSCRIBE /topic/room/{id}: enrolled member → live subscription and
 *      receives broadcasts; foreign user → ERROR frame
 *      "Forbidden: you are not a member of this room".
 *   3. SEND /app/chat/{id}: joined member → persists + broadcasts;
 *      enrolled-but-not-joined → ERROR frame + NO DB row; foreign → ERROR
 *      frame + NO DB row; owning mentor (not joined) → persists.
 * </ol>
 * These are the exact scenarios the frontend can experience — asserted over
 * the wire instead of through the clientInboundChannel shortcut used by the
 * in-process probes.
 */
@Tag("critical")
class WSAuthMatrixIntegrationTest extends WsStompIntegrationTestBase {

	private static final String MEMBER_EMAIL = "it-eb39-member@example.org";
	private static final String UNJOINED_EMAIL = "it-eb39-unjoined@example.org";
	private static final String FOREIGN_EMAIL = "it-eb39-foreign@example.org";
	private static final String MENTOR_EMAIL = "it-eb39-mentor@example.org";

	/* ---------- CONNECT states ---------- */

	@Test
	@DisplayName("EB39/AUTH[1] real socket: valid raw JWT CONNECT → session established")
	void connect_validToken_establishesSession() throws Exception {
		var ctx = seedRoom("connect-ok", MENTOR_EMAIL);
		TrackingConnection conn = connect(auth.authToken(MENTOR_EMAIL, AuthFixture.MENTOR));
		if (!conn.session().isConnected()) {
			throw new AssertionError("CONNECT did not establish a session");
		}
		conn.close();
	}

	@Test
	@DisplayName("EB39/AUTH[2] real socket: CONNECT without Authorization header → rejected")
	void connect_missingToken_rejected() {
		try {
			connect(null);
			throw new AssertionError("CONNECT without a token was accepted — auth broken");
		} catch (Exception expectedConnectFailure) {
			// contract: session setup fails at the connection level
		}
	}

	@Test
	@DisplayName("EB39/AUTH[3] real socket: CONNECT with garbage token → rejected")
	void connect_garbageToken_rejected() {
		try {
			connect("definitely-not-a-jwt");
			throw new AssertionError("CONNECT with a garbage token was accepted — auth broken");
		} catch (Exception expectedConnectFailure) {
			// contract: session setup fails at the connection level
		}
	}

	/* ---------- SUBSCRIBE (D-14 READ rule) ---------- */

	@Test
	@DisplayName("EB39/AUTH[4] real socket: enrolled member SUBSCRIBE → live, receives room broadcast")
	void subscribe_member_liveReceivesBroadcast() throws Exception {
		var ctx = seedRoom("sub-member", MENTOR_EMAIL);
		enroll(ctx.mentorshipId(), MEMBER_EMAIL);
		joinRoom(ctx.roomId(), MEMBER_EMAIL);

		// subscribe to the user error queue first — a compliant member must
		// stay silent there
		TrackingConnection member = connect(auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT));
		member.session().subscribe("/user/queue/errors", member.handler());
		member.session().subscribe("/topic/room/" + ctx.roomId(), member.handler());

		// member sends through the wire; the broadcast must come back on topic
		member.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "wire broadcast check"));
		Map<String, Object> frame = member.awaitFrame(
				f -> "wire broadcast check".equals(f.get("message")), 10);
		if (!"wire broadcast check".equals(frame.get("message"))) {
			throw new AssertionError("Broadcast frame lost: " + frame);
		}
		TimeUnit.SECONDS.sleep(1);
		if (!member.handler().userQueueFrames().isEmpty()) {
			throw new AssertionError("Compliant member received user-queue frames: "
					+ member.handler().userQueueFrames());
		}
		if (!member.handler().errorPayloads().isEmpty()) {
			throw new AssertionError("Compliant member received error frames: "
					+ member.handler().errorPayloads());
		}
	}

	@Test
	@DisplayName("EB39/AUTH[5] real socket: foreign user SUBSCRIBE → dropped + authored error on /user/queue/errors")
	void subscribe_foreign_errorOnQueue() throws Exception {
		var ctx = seedRoom("sub-foreign", MENTOR_EMAIL);
		auth.student(FOREIGN_EMAIL);

		TrackingConnection foreign = connect(auth.authToken(FOREIGN_EMAIL, AuthFixture.STUDENT));
		foreign.session().subscribe("/user/queue/errors", foreign.handler());
		foreign.session().subscribe("/topic/room/" + ctx.roomId(), foreign.handler());

		String error = foreign.awaitUserError("Forbidden: you are not a member of this room", 10);
		if (error == null) {
			throw new AssertionError("Expected the authored error on /queue/errors, got: " + error);
		}
	}

	/* ---------- SEND (D-14 WRITE rule) ---------- */
	@Test
	@DisplayName("EB39/AUTH[6] real socket: joined member SEND → persists (live broadcast probe [4] covers receive)")
	void send_joinedMember_persists() throws Exception {
		var ctx = seedRoom("send-member", MENTOR_EMAIL);
		enroll(ctx.mentorshipId(), MEMBER_EMAIL);
		joinRoom(ctx.roomId(), MEMBER_EMAIL);

		TrackingConnection member = connect(auth.authToken(MEMBER_EMAIL, AuthFixture.STUDENT));
		member.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "persisted from the wire"));

		String persisted = awaitOrNull(() -> {
			var rows = messageRepo.findMessagesByRoom(ctx.roomId(), null, PageRequest.of(0, 1));
			return rows.isEmpty() ? null : rows.get(0).getContent();
		}, 10);
		if (!"persisted from the wire".equals(persisted)) {
			throw new AssertionError("Joined member's SEND did not persist, got: " + persisted);
		}
	}

	@Test
	@DisplayName("EB39/AUTH[7] real socket: enrolled-but-not-joined SEND → dropped + authored error, no persistence")
	void send_enrolledNotJoined_errorFrameNoPersist() throws Exception {
		var ctx = seedRoom("send-unjoined", MENTOR_EMAIL);
		enroll(ctx.mentorshipId(), UNJOINED_EMAIL);

		TrackingConnection unjoined = connect(auth.authToken(UNJOINED_EMAIL, AuthFixture.STUDENT));
		unjoined.session().subscribe("/user/queue/errors", unjoined.handler());
		unjoined.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "write attempt"));

		String error = unjoined.awaitUserError("Forbidden: you are not a member of this room", 10);
		if (error == null) {
			throw new AssertionError("Expected the authored error on /queue/errors, got: " + error);
		}
		assertNoPersistence(ctx.roomId());
	}

	@Test
	@DisplayName("EB39/AUTH[8] real socket: foreign user SEND → dropped + authored error, no persistence")
	void send_foreign_errorFrameNoPersist() throws Exception {
		var ctx = seedRoom("send-foreign", MENTOR_EMAIL);
		auth.student(FOREIGN_EMAIL);

		TrackingConnection foreign = connect(auth.authToken(FOREIGN_EMAIL, AuthFixture.STUDENT));
		foreign.session().subscribe("/user/queue/errors", foreign.handler());
		foreign.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "cross-room write"));

		String error = foreign.awaitUserError("Forbidden: you are not a member of this room", 10);
		if (error == null) {
			throw new AssertionError("Expected the authored error on /queue/errors, got: " + error);
		}
		assertNoPersistence(ctx.roomId());
	}

	@Test
	@DisplayName("EB39/AUTH[9] real socket: owning mentor (not joined) SEND → persists")
	void send_owningMentor_persists() throws Exception {
		var ctx = seedRoom("send-mentor", MENTOR_EMAIL);

		TrackingConnection mentor = connect(auth.authToken(MENTOR_EMAIL, AuthFixture.MENTOR));
		mentor.session().send("/app/chat/" + ctx.roomId(), Map.of("content", "mentor broadcast"));

		String persisted = awaitOrNull(() -> {
			var rows = messageRepo.findMessagesByRoom(ctx.roomId(), null, PageRequest.of(0, 1));
			return rows.isEmpty() ? null : rows.get(0).getContent();
		}, 10);
		if (!"mentor broadcast".equals(persisted)) {
			throw new AssertionError("Owning mentor's SEND did not persist, got: " + persisted);
		}
	}

	/* ---------- helpers ---------- */

	private void assertNoPersistence(Long roomId) throws InterruptedException {
		TimeUnit.SECONDS.sleep(2); // give any (buggy) async save a chance to land
		var rows = messageRepo.findMessagesByRoom(roomId, null, PageRequest.of(0, 1));
		if (!rows.isEmpty()) {
			throw new AssertionError("Unauthorized SEND persisted into room " + roomId + ": "
					+ rows.get(0).getContent() + " — WS-01 regression");
		}
	}
}
