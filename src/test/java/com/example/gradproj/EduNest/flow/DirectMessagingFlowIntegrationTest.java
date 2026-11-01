package com.example.gradproj.EduNest.flow;

import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse;
import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-04 / EB-38 — Direct Messaging Flow Integration Suite (TST-01 payload 3).
 *
 * Covers:
 * 1. Happy-path: User A sends direct message to User B -> Conversation created/linked ->
 *    Message persisted -> User B views conversations list and message history ->
 *    User A edits message -> User A deletes message.
 * 2. Failure-path: Stranger cannot read messages of another user's conversation -> 403 Forbidden (CHAT-02).
 * 3. Failure-path: Stranger cannot edit another user's message -> 403 Forbidden (CHAT-03).
 * 4. Failure-path: Stranger cannot delete another user's message -> 403 Forbidden (CHAT-03).
 */
@Tag("critical")
@Tag("business-flow")
public class DirectMessagingFlowIntegrationTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private ConversationService conversationService;
	@Autowired private ConversationRepository conversations;
	@Autowired private MessageRepository messages;

	@Test
	@DisplayName("Flow 5 Happy Path: Send DM -> List conversations -> Read messages -> Edit message -> Delete message")
	void directMessagingFlow_happyPath() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String userAEmail = "flow-m5-a-" + unique + "@example.org";
		String userBEmail = "flow-m5-b-" + unique + "@example.org";

		Student userA = auth.student(userAEmail);
		Student userB = auth.student(userBEmail);

		String tokenA = auth.authToken(userAEmail, AuthFixture.STUDENT);
		String tokenB = auth.authToken(userBEmail, AuthFixture.STUDENT);

		// Step 1: User A sends private message to User B
		ConversationMessageResponse sentMsg = conversationService.sendMessage(
				userAEmail,
				ConversationMessageRequest.builder()
						.recipientEmail(userBEmail)
						.content("Hi there, let's collaborate on the project!")
						.build()
		);

		assertThat(sentMsg.getId()).isNotNull();
		assertThat(sentMsg.getConversationId()).isNotNull();
		Long conversationId = sentMsg.getConversationId();
		Long messageId = sentMsg.getId();

		// Step 2: User B lists conversations and sees User A
		mockMvc.perform(get("/api/v1/conversation/all")
						.header("Authorization", tokenB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.conversations").isArray())
				.andExpect(jsonPath("$.apiResponse.conversations[0].conversationId").value(conversationId));

		// Step 3: User B reads message history
		mockMvc.perform(get("/api/v1/conversation/{conversationId}/messages", conversationId)
						.header("Authorization", tokenB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.messages[0].id").value(messageId))
				.andExpect(jsonPath("$.apiResponse.messages[0].content").value("Hi there, let's collaborate on the project!"));

		// Step 4: User A edits message
		mockMvc.perform(patch("/api/v1/conversation/messages/{messageId}", messageId)
						.header("Authorization", tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Hi there, let's collaborate on Module 2!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiResponse.['message after update'].content").value("Hi there, let's collaborate on Module 2!"));

		Message updated = messages.findById(messageId).orElseThrow();
		assertThat(updated.getContent()).isEqualTo("Hi there, let's collaborate on Module 2!");

		// Step 5: User A deletes message
		mockMvc.perform(delete("/api/v1/conversation/messages/{messageId}", messageId)
						.header("Authorization", tokenA))
				.andExpect(status().isOk());

		assertThat(messages.findById(messageId)).isEmpty();
	}

	@Test
	@DisplayName("Flow 5 Failure Path: Stranger cannot read another user's conversation (403 Forbidden)")
	void directMessagingFlow_strangerCannotReadMessages() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String userAEmail = "flow-m5-sec-a-" + unique + "@example.org";
		String userBEmail = "flow-m5-sec-b-" + unique + "@example.org";
		String strangerEmail = "flow-m5-sec-stranger-" + unique + "@example.org";

		auth.student(userAEmail);
		auth.student(userBEmail);
		auth.student(strangerEmail);

		ConversationMessageResponse sentMsg = conversationService.sendMessage(
				userAEmail,
				ConversationMessageRequest.builder()
						.recipientEmail(userBEmail)
						.content("Private conversation between A and B")
						.build()
		);

		String strangerToken = auth.authToken(strangerEmail, AuthFixture.STUDENT);

		mockMvc.perform(get("/api/v1/conversation/{conversationId}/messages", sentMsg.getConversationId())
						.header("Authorization", strangerToken))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Flow 5 Failure Path: Stranger cannot edit or delete another user's message (403 Forbidden)")
	void directMessagingFlow_strangerCannotEditOrDeleteMessage() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String authorEmail = "flow-m5-author-" + unique + "@example.org";
		String recipientEmail = "flow-m5-recip-" + unique + "@example.org";
		String strangerEmail = "flow-m5-strg2-" + unique + "@example.org";

		auth.student(authorEmail);
		auth.student(recipientEmail);
		auth.student(strangerEmail);

		ConversationMessageResponse sentMsg = conversationService.sendMessage(
				authorEmail,
				ConversationMessageRequest.builder()
						.recipientEmail(recipientEmail)
						.content("Original message by author")
						.build()
		);

		String strangerToken = auth.authToken(strangerEmail, AuthFixture.STUDENT);

		// Stranger edit attempt fails
		mockMvc.perform(patch("/api/v1/conversation/messages/{messageId}", sentMsg.getId())
						.header("Authorization", strangerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Malicious edit attempt\"}"))
				.andExpect(status().isForbidden());

		// Stranger delete attempt fails
		mockMvc.perform(delete("/api/v1/conversation/messages/{messageId}", sentMsg.getId())
						.header("Authorization", strangerToken))
				.andExpect(status().isForbidden());

		// Message remains unmodified in repository
		Message untouched = messages.findById(sentMsg.getId()).orElseThrow();
		assertThat(untouched.getContent()).isEqualTo("Original message by author");
	}
}
