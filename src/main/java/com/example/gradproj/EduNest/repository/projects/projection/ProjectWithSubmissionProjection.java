package com.example.gradproj.EduNest.repository.projects.projection;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;

import java.time.LocalDateTime;

public interface ProjectWithSubmissionProjection {
    Long getProjectId();
    String getTitle();
    String getBrief();
    String getDescriptionUrl();
    Integer getPoints();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    String getGoal();

    Long getSubmissionId();
    SubmissionStatus getSubmissionStatus();
    Integer getFinalScore();
    Integer getTotalPoints();
    String getFileUrl();
    String getUploadedFilePath();
    String getFeedback();

    Long getMentorId();
    String getMentorName();
    String getMentorPhoto();
}
