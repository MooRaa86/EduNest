package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MentorShipListResponse {

    UUID getId();
    String getTitle();
    Double getRating();
    Long getTotalEnroll();
    Double getRevenue();
    LocalDateTime getCreatedDate();
    DifficultyLevel getDifficultyLevel();
    String getCoverImageUrl();
    String getMentorshipStatus();
}