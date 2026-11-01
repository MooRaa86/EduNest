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
 * P2-03 / EB-37 & P1-10 / EB-17 — G12/G13: room creation ownership gap + enrollment-as-membership proxy.
 */
@Tag("critical")
class RoomProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository chatRooms;

	private static final String FOREIGN_MENTOR = "probe-room-foreign-mentor@example.org";
	private static final String ENROLLED_NOT_JOINED = "probe-room-enrolled-not-joined@example.org";

	/**
	 * G12 (FIXED — EB-17 / P1-10, TD-07):
	 * Room creation requires caller to be the mentor who owns the mentorship.
	 * Foreign mentors receive 403 Forbidden.
	 */
	@Test
	@DisplayName("G12/fixed: foreign mentor creates a room in another mentor's mentorship → 403")
	void g12_foreignMentorCreatesRoomInForeignMentorship() throws Exception {
		var ownedChain = auth.mentorship("probe-room-owner-mentor@example.org", Status.ACTIVE);
		auth.mentor(FOREIGN_MENTOR);

		mockMvc.perform(post("/api/v1/chat-room/create/{mentorshipId}", ownedChain.mentorship().getId())
						.header("Authorization", auth.authToken(FOREIGN_MENTOR, AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"probe foreign room\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-10: owner mentor creates a room in their own mentorship → 200")
	void ownerMentorCreatesRoomInOwnMentorship_succeeds() throws Exception {
		var ownedChain = auth.mentorship("probe-room-owner2@example.org", Status.ACTIVE);

		mockMvc.perform(post("/api/v1/chat-room/create/{mentorshipId}", ownedChain.mentorship().getId())
						.header("Authorization", auth.authToken("probe-room-owner2@example.org", AuthFixture.MENTOR))
						.contentType("application/json")
						.content("{\"name\":\"probe owner room\"}"))
				.andExpect(status().isOk());
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
