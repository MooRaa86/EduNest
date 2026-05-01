package com.example.gradproj.EduNest.dto.dashboard.AdminDashboard;

import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardCards {
    private Long totalStudents;
    private Long totalMentors;
    private Long activeMentorships;
    private Long completedMentorships;
    private Double totalRevenue;
}
