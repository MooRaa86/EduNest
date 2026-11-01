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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-13 / EB-20 — Conversation IDOR trio + server-derived event routing:
 * G2 (CHAT-02), G3 (CHAT-03), and G4 (CHAT-04) probes.
 */
@Tag("critical")
class ConversationProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ConversationRepository conversations;
	@Autowired private MessageRepository messages;
	@Autowired private ChatRoomRepository chatRooms;
	@Autowired private ChatMessageRepository chatMessages;

	private static final String OWNER = "probe-conv-owner@example.org";
	private static final String PARTNER = "probe-conv-partner@example.org";
	private static final String STRANGER = "probe-conv-stranger@example.org";
	private static final String ADMIN = "probe-conv-admin@example.org";

	/**
	 * G2 / CHAT-02: Private conversation history read by a stranger → 403 Forbidden.
	 */
	@Test
	@DisplayName("G2/enforced: stranger reads a private conversation → 403 Forbidden (CHAT-02 fixed)")
	void g2_strangerReadsForeignConversation() throws Exception {
		auth.student(STRANGER);
		var owner = auth.student(OWNER);
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(auth.student(PARTNER))
				.createdAt(LocalDateTime.now()).build());
		messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("private message content").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(get("/api/v1/conversation/{id}/messages", conv.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("G2/enforced: participant reads conversation messages → 200 OK")
	void g2_participantReadsOwnConversation() throws Exception {
		var owner = auth.student("probe-part-owner@example.org");
		var partner = auth.student("probe-part-partner@example.org");
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(partner)
				.createdAt(LocalDateTime.now()).build());
		messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("hello partner").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(get("/api/v1/conversation/{id}/messages", conv.getId())
						.header("Authorization", auth.authToken(partner.getEmail(), AuthFixture.STUDENT)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].content").value("hello partner"))
				.andExpect(jsonPath("$.messages[0].recipientEmail").value(partner.getEmail()));
	}

	@Test
	@DisplayName("G2/enforced: admin reads any conversation messages → 200 OK")
	void g2_adminReadsAnyConversation() throws Exception {
		auth.admin(ADMIN);
		var owner = auth.student("probe-adm-owner@example.org");
		var partner = auth.student("probe-adm-partner@example.org");
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(partner)
				.createdAt(LocalDateTime.now()).build());
		messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("confidential chat").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(get("/api/v1/conversation/{id}/messages", conv.getId())
						.header("Authorization", auth.authToken(ADMIN, AuthFixture.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].content").value("confidential chat"));
	}

	/**
	 * G3 / CHAT-03: Edit a FOREIGNER's conversation message → 403 Forbidden.
	 */
	@Test
	@DisplayName("G3/enforced: stranger edits a foreigner's conversation message → 403 Forbidden (CHAT-03 fixed)")
	void g3_strangerEditsForeignConversationMessage() throws Exception {
		auth.student(STRANGER);
		var owner = auth.student(OWNER);
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(auth.student("probe-conv-partner2@example.org"))
				.createdAt(LocalDateTime.now()).build());
		Message msg = messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("original content").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(patch("/api/v1/conversation/messages/{id}", msg.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"content\":\"tampered by stranger\",\"recipientEmail\":\""
								+ owner.getEmail() + "\"}"))
				.andExpect(status().isForbidden());

		Message reloaded = messages.findById(msg.getId()).orElseThrow();
		assertEquals("original content", reloaded.getContent());
		assertFalse(reloaded.isUpdated(), "Unauthorized edit must not persist");
	}

	@Test
	@DisplayName("G3/enforced: sender edits own message → 200 OK with server-derived recipient (CHAT-04)")
	void g3_senderEditsOwnMessage() throws Exception {
		var owner = auth.student("probe-owner-edit@example.org");
		var partner = auth.student("probe-partner-edit@example.org");
		var spoofedVictim = "probe-spoofed-victim@example.org";
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(partner)
				.createdAt(LocalDateTime.now()).build());
		Message msg = messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("original text").sentAt(LocalDateTime.now()).build());

		// Even when client provides a spoofed recipient, server-derived recipient is the true partner
		mockMvc.perform(patch("/api/v1/conversation/messages/{id}", msg.getId())
						.header("Authorization", auth.authToken(owner.getEmail(), AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"content\":\"revised text\",\"recipientEmail\":\""
								+ spoofedVictim + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.['message after update'].content").value("revised text"))
				.andExpect(jsonPath("$.['message after update'].recipientEmail").value(partner.getEmail()));

		Message reloaded = messages.findById(msg.getId()).orElseThrow();
		assertEquals("revised text", reloaded.getContent());
		assertTrue(reloaded.isUpdated());
	}

	@Test
	@DisplayName("G3/enforced: stranger deletes foreigner's message → 403 Forbidden")
	void g3_strangerDeletesForeignMessage_rejected() throws Exception {
		auth.student(STRANGER);
		var owner = auth.student("probe-del-owner@example.org");
		var partner = auth.student("probe-del-partner@example.org");
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(partner)
				.createdAt(LocalDateTime.now()).build());
		Message msg = messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("keep me safe").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(delete("/api/v1/conversation/messages/{id}", msg.getId())
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"recipientEmail\":\"" + partner.getEmail() + "\"}"))
				.andExpect(status().isForbidden());

		assertTrue(messages.findById(msg.getId()).isPresent(), "Foreign message must not be deleted");
	}

	@Test
	@DisplayName("G3/enforced: sender deletes own message → 200 OK")
	void g3_senderDeletesOwnMessage() throws Exception {
		var owner = auth.student("probe-del-self@example.org");
		var partner = auth.student("probe-del-partner-self@example.org");
		Conversation conv = conversations.saveAndFlush(Conversation.builder()
				.user1(owner).user2(partner)
				.createdAt(LocalDateTime.now()).build());
		Message msg = messages.saveAndFlush(Message.builder().conversation(conv).sender(owner)
				.content("delete me").sentAt(LocalDateTime.now()).build());

		mockMvc.perform(delete("/api/v1/conversation/messages/{id}", msg.getId())
						.header("Authorization", auth.authToken(owner.getEmail(), AuthFixture.STUDENT))
						.contentType("application/json")
						.content("{\"recipientEmail\":\"" + partner.getEmail() + "\"}"))
				.andExpect(status().isOk());

		assertFalse(messages.findById(msg.getId()).isPresent(), "Message must be deleted");
	}

	/**
	 * G3b — edit a FOREIGNER's chat-room message (room variant).
	 */
	@Test
	@DisplayName("G3b/todayEnforced: stranger edits a foreigner's chat-room message → 400")
	void g3b_strangerEditsForeignRoomMessage() throws Exception {
		auth.student(STRANGER);
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
