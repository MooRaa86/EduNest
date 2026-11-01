package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G12/G13: room creation ownership + enrollment-as-membership proxy.
 * G12 half FLIPPED/CLOSED by EB-17 (P1-10) — see the test note; the G13 pin
 * stays "todayOpen" until the WS membership semantics land (EB-19 / P1-12).
 */
@Tag("critical")
class RoomProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository chatRooms;

	private static final String FOREIGN_MENTOR = "probe-room-foreign-mentor@example.org";
	private static final String ENROLLED_NOT_JOINED = "probe-room-enrolled-not-joined@example.org";

	/**
	 * G12 — any MENTOR can create a room in ANY mentorship (mentorship-ownership unchecked).
	 * Audit: ChatRoomService.createRoom L43-79 — mentor gate only.
	 * EXPECTED TODAY (pre-fix pin): 2xx room created for a mentorship owned by a different mentor.
	 * FLIPPED — CLOSED by [P1-10] / EB-17 (2026-11-01): createRoom now enforces
	 * SecurityService#isMentorOwnMentorship before creation → foreign (and
	 * non-existent) mentorship ids answer 403. The full post-fix matrix
	 * (no-persistence check, owner 200, unknown-id 403, dashboard role gates)
	 * lives in MethodSecuritySweepProbesTest.
	 */
	@Test
	@DisplayName("G12/closed-EB17: foreign mentor creating a room in another mentor's mentorship → 403")
	void g12_foreignMentorCreatesRoomInForeignMentorship() throws Exception {
		var ownedChain = auth.mentorship("probe-room-owner-mentor@example.org", Status.ACTIVE);
		auth.mentor(FOREIGN_MENTOR);

		mockMvc.perform(post("/api/v1/chat-room/create/{mentorshipId}", ownedChain.mentorship().getId())
						.header("Authorization", auth.authToken(FOREIGN_MENTOR, AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"probe foreign room\"}"))
				.andExpect(status().isForbidden());
	}

	/**
	 * G13 — "membership" is a mentorship-enrollment proxy: an enrolled student who
	 * NEVER JOINED a room can still read it.
	 * Audit: AuthorizationRepository.isUserMemberOfChatRoom L82-93.
	 * EXPECT TODAY: 200 (message list served to the never-joined member).
	 * FLIPS WITH: [P1-12] WS room authorization + membership semantics (EB-19).
	 */
	@Test
	@DisplayName("G13/todayOpen: enrolled-but-never-joined student reads a room → 200")
	void g13_enrolledNeverJoinedReadsRoom() throws Exception {
		var chain = auth.mentorship("probe-g13-mentor@example.org", Status.ACTIVE);
		auth.enroll(chain.mentorship().getId(), ENROLLED_NOT_JOINED);
		ChatRoom room = chatRooms.saveAndFlush(ChatRoom.builder()
				.name("probe g13 room").mentorship(chain.mentorship())
				.creator(chain.mentor()).build());

		mockMvc.perform(get("/api/v1/chat-room/{roomId}/messages", room.getId())
						.header("Authorization", auth.authToken(ENROLLED_NOT_JOINED, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}
}
