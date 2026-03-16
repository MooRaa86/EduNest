package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.Status;

import java.time.LocalDateTime;

public interface ContinueLearningProjection {
    Long getMentorshipId();
    String getTitle();
    String getCoverImageUrl();
    String getMentorName();
    Status getStatus();
    Integer getTotalWeeks();
    LocalDateTime getJoinedAt();
}
