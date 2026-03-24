package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.Status;

public interface MentorshipStatsResponse {

    String getTitle();
    Status getStatus();
    Long getTotalLessons();
    Long getTotalQuizzes();
    Long getTotalAssignments();
    Long getTotalSessions();
    Long getTotalProjects();
}
