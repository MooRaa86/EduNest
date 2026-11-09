package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class ChatMessageResponse {
    UUID id;
    String message;
    String senderName;
    String senderEmail;
    String senderProfileImageUrl;
    UUID roomId;
    LocalDateTime time;

    public static class ChatMessageResponseBuilder {
        public ChatMessageResponseBuilder id(Long id) {
            this.id = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ChatMessageResponseBuilder id(UUID id) {
            this.id = id;
            return this;
        }
        public ChatMessageResponseBuilder roomId(Long id) {
            this.roomId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ChatMessageResponseBuilder roomId(UUID id) {
            this.roomId = id;
            return this;
        }
    }
}
