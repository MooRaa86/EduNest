package com.example.gradproj.EduNest.repository.mentorShip.projections;

public interface AdminDashboardCardsProjection {
    Long getTotalStudents();
    Long getTotalMentors();
    Long getActiveMentorships();
    Long getCompletedMentorships();
    Double getTotalRevenue();
}
