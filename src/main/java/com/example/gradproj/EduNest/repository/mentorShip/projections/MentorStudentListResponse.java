package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface MentorStudentListResponse {
    String getProfileImageUrl();

    java.util.UUID getStudentId();
    String getFirstName();
    String getLastName();
    String getEmail();

    Long getActiveMentorshipCount();
    Long getCompletedMentorshipCount();
}