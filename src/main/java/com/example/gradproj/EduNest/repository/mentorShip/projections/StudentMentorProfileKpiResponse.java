package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface StudentMentorProfileKpiResponse {
    Integer getTotalPoints();

    Long getActiveMentorships();

    Long getCompletedMentorships();
}
