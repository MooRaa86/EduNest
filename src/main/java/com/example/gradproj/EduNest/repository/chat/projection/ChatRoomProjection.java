package com.example.gradproj.EduNest.repository.chat.projection;

import java.time.LocalDateTime;

public interface ChatRoomProjection {

    Long getRoomId();

    String getRoomName();

    String getRoomImageUrl();

    Long getMentorshipId();

    String getMentorshipName();

    String getCreatorEmail();

    String getCreatorName();

    String getLastMessageContent();

    LocalDateTime getLastMessageTime();

    String getLastMessageSenderEmail();

    String getLastMessageSenderName();
}
