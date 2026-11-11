package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ChatMessageProjection {

    UUID getMessageId();

    String getContent();

    LocalDateTime getCreatedAt();

    String getSenderEmail();

    String getSenderName();

    String getSenderProfileImageUrl();
}
