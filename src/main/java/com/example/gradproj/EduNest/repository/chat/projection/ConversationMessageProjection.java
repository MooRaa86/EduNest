package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ConversationMessageProjection {

    Long getMessageId();

    String getContent();

    LocalDateTime getSentAt();

    String getSenderEmail();

    String getSenderName();

    String getSenderProfileImageUrl();

    Boolean getSenderDeleted();

    UUID getConversationId();
}
