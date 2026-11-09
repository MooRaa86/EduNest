package com.example.gradproj.EduNest.repository.projects.projection;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProjectWithSubmissionProjection {
    UUID getProjectId();
    String getTitle();
    String getBrief();
    String getDescriptionUrl();
    String getAttachmentPath();
    Integer getPoints();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    String getGoal();

    UUID getSubmissionId();
    SubmissionStatus getSubmissionStatus();
    Integer getFinalScore();
    Integer getTotalPoints();
    String getFileUrl();
    String getUploadedFilePath();
    String getFeedback();

    UUID getMentorId();
    String getMentorName();
    String getMentorPhoto();
}
