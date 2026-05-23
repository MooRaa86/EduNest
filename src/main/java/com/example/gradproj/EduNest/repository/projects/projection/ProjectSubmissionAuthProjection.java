package com.example.gradproj.EduNest.repository.projects.projection;

public interface ProjectSubmissionAuthProjection {
    Long getId();
    String getStudentEmail();
    String getMentorEmail();
    String getFilePath();
}
