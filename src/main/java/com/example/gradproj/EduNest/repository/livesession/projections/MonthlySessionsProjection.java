package com.example.gradproj.EduNest.repository.livesession.projections;

public interface MonthlySessionsProjection {
    Integer getYear();
    Integer getMonth();
    Long getTotalSessions();
}
