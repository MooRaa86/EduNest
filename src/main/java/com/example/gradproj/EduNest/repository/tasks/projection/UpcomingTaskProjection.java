package com.example.gradproj.EduNest.repository.tasks.projection;

import java.time.LocalDateTime;

public interface UpcomingTaskProjection {
    Long getId();
    String getTitle();
    LocalDateTime getDueAt();
    Integer getPoints();
    Long getWeekId();
    String getWeekTitle();
    Long getMentorshipId();
    String getMentorshipTitle();
}
