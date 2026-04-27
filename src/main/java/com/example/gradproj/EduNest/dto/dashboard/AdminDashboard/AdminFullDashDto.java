package com.example.gradproj.EduNest.dto.dashboard.AdminDashboard;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.repository.livesession.projections.MonthlySessionsProjection;
import com.example.gradproj.EduNest.repository.users.projection.TopMentorProjection;
import lombok.*;

import java.util.List;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminFullDashDto {
    private AdminDashboardCards cards;
    private List<MonthlySessionsProjection> sessionsChart;
    private PageResponse<AdminNotificationResponse> notifications;
    private PageResponse<TopMentorProjection> topMentors;
}
