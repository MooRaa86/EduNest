package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ConversationListProjection {

    UUID getConversationId();

    String getOtherUserEmail();

    String getOtherUserName();

    String getOtherUserProfileImageUrl();

    String getLastMessage();

    LocalDateTime getLastMessageTime();
}
