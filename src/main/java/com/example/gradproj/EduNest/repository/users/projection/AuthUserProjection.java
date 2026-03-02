package com.example.gradproj.EduNest.repository.users.projection;

public interface AuthUserProjection {

    Long getId();

    String getEmail();

    String getPassword();

    Boolean getEnabled();

    String getRoleName();

    Boolean getDeleted();
}
