package com.example.gradproj.EduNest.repository.projects.projection;

public interface ProjectSubmissionAuthProjection {
    Long getId();
    Long getWeekId();
    Long getProjectId();
    String getStudentEmail();
    String getMentorEmail();
    String getFilePath();
}
