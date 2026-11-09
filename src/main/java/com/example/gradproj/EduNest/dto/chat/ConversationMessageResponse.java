package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter @Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageResponse {

    private Long id;
    private UUID conversationId;
    private UUID senderId;
    private String senderEmail;
    private String senderName;
    private String senderProfileImageUrl;
    private UUID recipientId;
    private String recipientEmail;
    private String content;
    private LocalDateTime sentAt;

    public static class ConversationMessageResponseBuilder {
        public ConversationMessageResponseBuilder conversationId(Long id) {
            this.conversationId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ConversationMessageResponseBuilder conversationId(UUID id) {
            this.conversationId = id;
            return this;
        }
        public ConversationMessageResponseBuilder senderId(Long id) {
            this.senderId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ConversationMessageResponseBuilder senderId(UUID id) {
            this.senderId = id;
            return this;
        }
        public ConversationMessageResponseBuilder recipientId(Long id) {
            this.recipientId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ConversationMessageResponseBuilder recipientId(UUID id) {
            this.recipientId = id;
            return this;
        }
    }
}
