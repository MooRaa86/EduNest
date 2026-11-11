package com.example.gradproj.EduNest.repository.tasks.projection;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TaskWithSubmissionProjection {
    UUID getTaskId();
    String getTaskTitle();
    Integer getPoints();
    LocalDateTime getDueAt();
    String getDescription();
    String getAttachmentUrl();
    String getUploadedAttachmentPath();
    Integer getEstimatedMinutes();

    UUID getSubmissionId();
    String getFileUrl();
    Integer getFinalScore();
    Integer getTotalPoints();
    SubmissionStatus getSubmissionStatus();
    String getUploadedSubmissionPath();
    String getFeedback();
    String getMentorName();
    String getMentorPhoto();
}
