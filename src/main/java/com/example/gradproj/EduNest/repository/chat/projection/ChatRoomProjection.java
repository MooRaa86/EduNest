package com.example.gradproj.EduNest.repository.chat.projection;

public interface ChatRoomProjection {

    Long getRoomId();

    String getRoomName();

    Long getMentorshipId();

    String getMentorshipName();

    String getCreatorEmail();

    String getCreatorName();
}
