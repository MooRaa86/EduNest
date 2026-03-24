package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;

public interface MentorshipOverviewProjection {
    Long getId();
    String getTitle();
    String getSubtitle();
    String getDescription();
    String getCategory();
    DifficultyLevel getDifficultyLevel();
    Double getDuration();
    Double getPrice();
    Integer getDiscountPercentage();
    String getCoverImageUrl();
    Status getStatus();
    Double getRating();
    String getMentorName();
    String getMentorEmail();
    String getMentorProfileImageUrl();
    Integer getMentorYearsOfExperience();
    Boolean getIsEnrolled();
}
