package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface ContinueLearningWithProgressProjection {
    Long getMentorshipId();
    /**
     * EB-60 / P4-11 — F4: carried inline so HomePageService no longer looks it
     * up per row. Typed as String (CAST in the query) because H2 in MySQL mode
     * hands uuid columns to native queries as byte[], which Spring Data cannot
     * project to UUID; the service parses it.
     */
    String getMentorshipPublicId();
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
