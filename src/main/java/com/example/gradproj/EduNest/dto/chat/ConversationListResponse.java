package com.example.gradproj.EduNest.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListResponse {

    private UUID conversationId;
    private String otherUserEmail;
    private String otherUserName;
    private String otherUserProfileImageUrl;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
}
