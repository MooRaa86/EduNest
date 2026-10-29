package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — Option A probes: TODAY's reality, per-test expectations flip
 * when the fix tasks land.
 */
@Tag("critical")
class ConversationProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ConversationRepository conversations;
	@Autowired private MessageRepository messages;
	@Autowired private ChatRoomRepository chatRooms;
	@Autowired private ChatMessageRepository chatMessages;

	private static final String OWNER = "probe-conv-owner@example.org";
	private static final String STRANGER = "probe-conv-stranger@example.org";

	/**
	 * G2 — private conversation history read by a foreigner.
	 * Audit: ConversationService.getConversationMessages has NO ownership check.
	 * EXPECT TODAY: 200 + the foreign conversation's messages leak.
	 * FLIPS WITH: [P1-13] conversation IDOR (EB-20) → expect 403 then.
	 */
	@Test
	@DisplayName("G2/todayOpen: stranger reads a private conversation → 200 (no ownership check)")
	void g2_strangerReadsForeignConversation() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		var owner = auth.student(OWNER);
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(auth.student("probe-conv-partner@example.org"))
				.createdAt(LocalDateTime.now()).build());
		messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("private message content").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(get("/api/v1/conversation/{id}/messages", conv.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	/**
	 * G3 — edit a FOREIGNER's conversation message.
	 * Audit: ConversationService.editMessage ownership check commented out (L119-121).
	 * EXPECT TODAY: 200, stranger successfully edits.
	 * FLIPS WITH: [P1-13] (EB-20) → expect 403 then.
	 */
	@Test
	@DisplayName("G3/todayOpen: stranger edits a foreigner's conversation message → 200")
	void g3_strangerEditsForeignConversationMessage() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		var owner = auth.student(OWNER);
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(auth.student("probe-conv-partner2@example.org"))
				.createdAt(LocalDateTime.now()).build());
		Message msg = messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("original content").sentAt(LocalDateTime.now()).build());

		var result = mockMvc.perform(patch("/api/v1/conversation/messages/{id}", msg.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"content\":\"tampered by stranger\",\"recipientEmail\":\""
								+ owner.getEmail() + "\"}"))
				.andReturn();

		org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus(),
				"G3 audit expects no ownership gate — the edit goes through. Response body: "
						+ result.getResponse().getContentAsString());

		Message reloaded = messages.findById(msg.getId()).orElseThrow();
		assertTrue(reloaded.isUpdated(), "G3 audit expects no ownership gate — the edit goes through");
	}

	/**
	 * G3b — edit a FOREIGNER's chat-room message (room variant).
	 * ⚠️ REGISTER CORRECTION (probe-adjudicated): unlike the conversation variant
	 * (G3), the CHAT-ROOM surface DOES enforce sender-only today
	 * (ChatMessageService.editMessage L130-132 throws globalLogicEx for a foreign
	 * sender → today's logic-exception dialect 400). The remaining gap on this
	 * surface is ROOM MEMBERSHIP (see G13), not sender identity.
	 * EXPECT TODAY: 400 "Not allowed to edit this message".
	 * REVISIT WITH: [P1-12] (EB-19) when membership semantics unify — expectations
	 * may restrengthen (403) with the error contract (EB-12).
	 */
	@Test
	@DisplayName("G3b/todayEnforced: stranger edits a foreigner's chat-room message → 400")
	void g3b_strangerEditsForeignRoomMessage() throws Exception {
		auth.student(STRANGER); // token-holder must exist in DB (validator checks users table)
		var owner = auth.student("probe-room-owner@example.org");
		var mentor = auth.mentor("probe-room-mentor@example.org");
		var chain = auth.mentorship("probe-room-mentor@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
		ChatRoom room = chatRooms.saveAndFlush(ChatRoom.builder()
				.name("probe-room").mentorship(chain.mentorship()).creator(mentor).build());
		ChatMessage msg = chatMessages.saveAndFlush(ChatMessage.builder().content("hi owner").sender(owner)
				.senderEmail(owner.getEmail()).senderName("owner").chatRoom(room).build());

		mockMvc.perform(patch("/api/v1/chat-room/{roomId}/messages/{messageId}", room.getId(), msg.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.param("content", "tampered by stranger"))
				.andExpect(status().isBadRequest());
	}
}
