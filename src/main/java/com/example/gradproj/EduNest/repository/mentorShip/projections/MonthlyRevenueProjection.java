package com.example.gradproj.EduNest.repository.mentorShip.projections;


public interface MonthlyRevenueProjection {
    Integer getYear();
    Integer getMonth();
    Double getTotalRevenue();
}
