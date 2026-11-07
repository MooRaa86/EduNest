package com.example.gradproj.EduNest.repository.users.projection;

import java.util.UUID;

public interface UserFullNameProjection {
    UUID getPublicId();
    String getEmail();
    String getFullName();
}
