package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.Status;

public interface EnrolledMentorshipProgressResponse {
    String getImageUrl();
    Long getMentorshipId();
    String getTitle();
    Status getStatus();

    Integer getTotalPoints();          // points الطالب في mentorship دي

    Long getTotalTasks();
    Long getSubmittedTasks();

    Long getTotalQuizzes();
    Long getSubmittedQuizzes();
}
