package com.example.gradproj.EduNest.repository.tasks.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UpcomingTaskProjection {
    UUID getId();
    String getTitle();
    LocalDateTime getDueAt();
    Integer getPoints();
    UUID getWeekId();
    String getWeekTitle();
    UUID getMentorshipId();
    String getMentorshipTitle();
}
