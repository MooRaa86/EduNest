package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;

public interface RecommendedMentorshipProjection {
    // Native UUID columns are returned as byte[] by H2; the query casts to text.
    // JSON represents the PostgreSQL UUID and this string identically.
    String getId();
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
