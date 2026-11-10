package com.example.gradproj.EduNest.repository.projects.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UpcomingProjectProjection {
    UUID getId();
    String getTitle();
    LocalDateTime getEndAt();
    Integer getPoints();
    UUID getWeekId();
    String getWeekTitle();
    UUID getMentorshipId();
    String getMentorshipTitle();
}
