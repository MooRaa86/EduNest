package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;

public interface ConversationListProjection {

    Long getConversationId();

    String getOtherUserEmail();

    String getOtherUserName();

    String getOtherUserProfileImageUrl();

    String getLastMessage();

    LocalDateTime getLastMessageTime();
}