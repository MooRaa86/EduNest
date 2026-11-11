package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ChatRoomProjection {

    UUID getRoomId();

    String getRoomName();

    String getRoomImageUrl();

    UUID getMentorshipId();

    String getMentorshipName();

    String getCreatorEmail();

    String getCreatorName();

    String getLastMessageContent();

    LocalDateTime getLastMessageTime();

    String getLastMessageSenderEmail();

    String getLastMessageSenderName();
}
