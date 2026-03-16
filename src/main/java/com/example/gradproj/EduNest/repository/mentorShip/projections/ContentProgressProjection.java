package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface ContentProgressProjection {
    Long getTotalTasks();
    Long getCompletedTasks();
    Long getTotalQuizzes();
    Long getCompletedQuizzes();
    Long getTotalProjects();
    Long getCompletedProjects();
}
