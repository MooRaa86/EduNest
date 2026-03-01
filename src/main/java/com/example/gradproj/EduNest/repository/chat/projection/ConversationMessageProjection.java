package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;

public interface ConversationMessageProjection {

    Long getMessageId();

    String getContent();

    LocalDateTime getSentAt();

    String getSenderEmail();

    String getSenderName();

    Long getConversationId();
}
