package com.example.gradproj.EduNest.repository.users.projection;

public interface UserListProjection {
    Long getId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getRoleName();
    String getProfileImageUrl();
    Boolean getEnabled();
}
