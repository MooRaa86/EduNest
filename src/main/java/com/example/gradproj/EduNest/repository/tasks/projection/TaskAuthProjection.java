package com.example.gradproj.EduNest.repository.tasks.projection;

public interface TaskAuthProjection {
    Long getId();
    String getMentorEmail();
    Long getMentorshipId();
    String getFilePath();
}
