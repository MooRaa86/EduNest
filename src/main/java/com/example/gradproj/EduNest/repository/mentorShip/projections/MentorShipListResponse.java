package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;

import java.time.LocalDateTime;

public interface MentorShipListResponse {

    Long getId();
    String getTitle();
    Double getRating();
    Long getTotalEnroll();
    Double getRevenue();
    LocalDateTime getCreatedDate();
    DifficultyLevel getDifficultyLevel();
}