package com.example.gradproj.EduNest.repository.tasks.projection;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;

import java.time.LocalDateTime;

public interface TaskWithSubmissionProjection {
    Long getTaskId();
    String getTaskTitle();
    Integer getPoints();
    LocalDateTime getDueAt();
    String getDescription();
    String getAttachmentUrl();
    String getUploadedAttachmentPath();
    Integer getEstimatedMinutes();

    String getFileUrl();
    Integer getFinalScore();
    Integer getTotalPoints();
    SubmissionStatus getSubmissionStatus();
    String getUploadedSubmissionPath();
    String getFeedback();
    String getMentorName();
    String getMentorPhoto();
}
