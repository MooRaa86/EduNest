package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface MentorshipReviewProjection {
    java.util.UUID getReviewId();
        String getFeedback();
    Long getRating();
    String getStudentFullName();
    String getUserProfileImageUrl();
    String getStudentEmail();
}
