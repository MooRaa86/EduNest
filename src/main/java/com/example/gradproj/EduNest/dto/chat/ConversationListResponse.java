package com.example.gradproj.EduNest.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
public class ConversationListResponse {

    private Long conversationId;
    private String otherUserEmail;
    private String otherUserName;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}