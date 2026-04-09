package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface ActiveMentorshipProgressProjection {
    Long getMentorshipId();
    String getTitle();
    String getSubtitle();
    String getCategory();
    String getDifficultyLevel();
    String getCoverImageUrl();
    Integer getTotalPoints();

    Long getTotalTasks();
    Long getSubmittedTasks();
    Long getTotalQuizzes();
    Long getSubmittedQuizzes();
    Long getTotalProjects();
    Long getSubmittedProjects();
    Long getTotalLectures();
    String getStatus();
}
