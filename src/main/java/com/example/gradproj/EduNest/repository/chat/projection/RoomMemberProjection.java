package com.example.gradproj.EduNest.repository.chat.projection;

import java.util.UUID;

public interface RoomMemberProjection {

    UUID getUserId();

    String getEmail();

    String getFirstName();

    String getLastName();

    String getRole();

    String getUserImageUrl();
}
