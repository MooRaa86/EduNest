package com.example.gradproj.EduNest.repository.projects.projection;

import com.example.gradproj.EduNest.enums.project.ProjectStatus;

import java.time.LocalDateTime;

public interface ProjectWithStatsProjection {
    Long getId();
    String getTitle();
    String getGoal();
    String getBrief();
    String getDescriptionUrl();
    String getUploadedAttachmentPath();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    Integer getPoints();
    ProjectStatus getStatus();
    Long getWeekId();
    LocalDateTime getCreatedAt();
    Long getTotalStudents();
    Long getSubmissionsCount();
}
