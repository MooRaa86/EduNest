package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;

import java.util.UUID;

public interface MentorshipDetailsProjection {
    UUID getId();
    UUID getPublicId();
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
    UUID getMentorId();
    String getMentorName();
    String getMentorEmail();
    String getMentorProfileImageUrl();
    String getMentorJobTitle();
    Integer getMentorYearsOfExperience();
}
