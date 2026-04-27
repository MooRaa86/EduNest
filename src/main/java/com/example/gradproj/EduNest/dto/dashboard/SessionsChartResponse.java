package com.example.gradproj.EduNest.dto.dashboard;

public record SessionsChartResponse(
        String month,
        Integer year,
        Long totalSessions
) {}
