package com.example.gradproj.EduNest.repository.users.projection;

public interface MentorStatsProjection {
    Long getTotalSessions();
    Long getTotalStudents();
    Double getAverageRating();
    Long getTotalBadges();
    Long getMentorshipCount();
}
