package com.example.gradproj.EduNest.dto.dashboard;

public record SalesChartResponse(
        String month,
        Integer year,
        Double totalRevenue
) {}