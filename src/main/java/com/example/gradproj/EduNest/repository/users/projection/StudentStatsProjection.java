package com.example.gradproj.EduNest.repository.users.projection;

public interface StudentStatsProjection {
    Long getTotalEnrollments();
    Long getTotalCompletedMentorships();
    Long getTotalBadgesEarned();
}
