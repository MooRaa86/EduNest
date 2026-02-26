package com.example.gradproj.EduNest.dto.dashboard;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCardsResponse {

    private Long totalStudents;

    private Long totalMentorships;

    private Double averageRating;

    private Double totalRevenue;
}