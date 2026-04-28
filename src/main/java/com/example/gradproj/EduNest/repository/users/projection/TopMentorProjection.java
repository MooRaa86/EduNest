package com.example.gradproj.EduNest.repository.users.projection;

public interface TopMentorProjection {
    String getFullName();
    String getEmail();
    String getProfileImageUrl();
    Long getTotalStudents();
    Double getTotalRevenue();
}
