package com.example.gradproj.EduNest.repository.tasks.projection;

public interface TaskSubmissionAuthProjection {
    Long getId();
    String getStudentEmail();
    String getMentorEmail();
    String getFilePath();
}
