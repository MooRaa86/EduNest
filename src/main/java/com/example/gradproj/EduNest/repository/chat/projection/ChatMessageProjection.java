package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;

public interface ChatMessageProjection {

    Long getMessageId();

    String getContent();

    LocalDateTime getCreatedAt();

    String getSenderEmail();

    String getSenderName();
}
