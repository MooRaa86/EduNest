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
}
