package com.example.gradproj.EduNest.repository.tasks.projection;

public interface TaskSubmissionAuthProjection {
    Long getId();
    Long getWeekId();
    Long getTaskId();
    String getStudentEmail();
    String getMentorEmail();
    String getFilePath();
}
