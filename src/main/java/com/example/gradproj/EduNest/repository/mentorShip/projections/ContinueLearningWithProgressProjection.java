package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface ContinueLearningWithProgressProjection {
    Long getMentorshipId();
    String getTitle();
    String getCoverImageUrl();
    String getMentorName();
    Long getTotalTasks();
    Long getCompletedTasks();
    Long getTotalQuizzes();
    Long getCompletedQuizzes();
    Long getTotalProjects();
    Long getCompletedProjects();
}
