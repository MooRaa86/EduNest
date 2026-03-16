package com.example.gradproj.EduNest.repository.projects.projection;

import java.time.LocalDateTime;

public interface UpcomingProjectProjection {
    Long getId();
    String getTitle();
    LocalDateTime getEndAt();
    Integer getPoints();
    Long getWeekId();
    String getWeekTitle();
    Long getMentorshipId();
    String getMentorshipTitle();
}
