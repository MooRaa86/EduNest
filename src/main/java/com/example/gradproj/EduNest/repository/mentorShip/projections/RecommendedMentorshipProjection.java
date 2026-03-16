package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;

public interface RecommendedMentorshipProjection {
    Long getId();
    String getTitle();
    String getSubtitle();
    String getDescription();
    String getDifficultyLevel();
    Double getDuration();
    Double getPrice();
    Integer getDiscountPercentage();
    String getCoverImageUrl();
    String getMentorName();
    String getMentorEmail();
}
