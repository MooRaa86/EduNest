package com.example.gradproj.EduNest.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter @Getter
@Builder
public class ConversationMessageResponse {

    private Long id;
    private Long conversationId;
    private String senderEmail;
    private String senderName;
    private String content;
    private LocalDateTime sentAt;
}
