package com.example.gradproj.EduNest.repository.mentorShip.projections;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import java.util.UUID;

public interface EnrolledMentorshipProgressResponse {
    String getImageUrl();
    UUID getMentorshipId();
    String getTitle();
    Status getStatus();

    Integer getTotalPoints();          // points الطالب في mentorship دي

    Long getTotalTasks();
    Long getSubmittedTasks();

    Long getTotalQuizzes();
    Long getSubmittedQuizzes();
}
