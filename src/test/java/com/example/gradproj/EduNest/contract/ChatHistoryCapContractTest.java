package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-09 / EB-58 — cursor-paginated chat surfaces keep their pageSize semantics
 * but gain a hard size cap.
 *
 * The chat history endpoint is cursor-based ({@code beforeId} + {@code size},
 * page fixed at 0) — it returns a bare {@code List}, not a {@code PageResponse},
 * so its response has no envelope {@code size} to clamp. The P4-09 contract for
 * cursored surfaces is therefore: the REQUESTED page size is capped (default 20
 * preserved; oversized requests capped at the global 50) while cursor semantics
 * stay untouched.
 *
 * Seeded with 60 real rows in a real room reached through the prod-chain
 * membership query (Enrollment JOIN ChatRoom ON mentorship) — the cap must be
 * observable as a bounded query result, not just a field rewrite.
 */
@Tag("critical")
class ChatHistoryCapContractTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ChatRoomRepository rooms;
	@Autowired private ChatMessageRepository messages;
	@Autowired private ChatMessageService chatMessageService;

	@Test
	@DisplayName("P4-09: chat history size=5000 → capped at 50 rows (default 20 / cursor semantics preserved)")
	void oversizedChatPageSize_cappedAt50() {
		String mentorEmail = "eb58-chat-mentor-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org";
		String studentEmail = "eb58-chat-student-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.org";
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		var student = auth.enroll(chain.mentorship().getId(), studentEmail).getStudent();

		ChatRoom room = rooms.saveAndFlush(ChatRoom.builder()
				.name("eb58-cap-room")
				.mentorship(chain.mentorship())
				.creator(student)
				.build());

		for (int i = 1; i <= 60; i++) {
			messages.saveAndFlush(ChatMessage.builder()
					.chatRoom(room)
					.sender(student)
					.senderEmail(studentEmail)
					.senderName("eb58 student")
					.content("msg-" + i)
					.build());
		}

		Long roomId = rooms.findByPublicId(room.getPublicId()).orElseThrow().getId();

		assertThat(chatMessageService.getRoomMessages(roomId, null, 20, studentEmail)).hasSize(20);
		assertThat(chatMessageService.getRoomMessages(roomId, null, 5000, studentEmail)).hasSize(50);
		assertThat(chatMessageService.getRoomMessages(roomId, null, 0, studentEmail)).hasSize(1);

		Long newestId = messages.findAll().stream()
				.filter(m -> m.getChatRoom().getId().equals(roomId))
				.map(ChatMessage::getId)
				.max(Long::compareTo)
				.orElseThrow();
		var cursorPage = chatMessageService.getRoomMessages(roomId, newestId, 50, studentEmail);
		assertThat(cursorPage).hasSize(50);
		assertThat(cursorPage.get(0).getMessage()).isEqualTo("msg-59");
	}
}
