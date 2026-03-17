package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface MentorshipReviewProjection {
    Long getReviewId();
    String getFeedback();
    Long getRating();
    String getStudentFullName();
    String getStudentProfileImageUrl();
    String getStudentEmail();
}
