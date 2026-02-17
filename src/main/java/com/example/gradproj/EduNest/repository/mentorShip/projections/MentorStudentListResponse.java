package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface MentorStudentListResponse {

    Long getStudentId();
    String getFirstName();
    String getLastName();
    String getEmail();

    Long getActiveMentorshipCount();
    Long getCompletedMentorshipCount();
}