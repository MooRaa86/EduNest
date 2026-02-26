package com.example.gradproj.EduNest.dto.dashboard;

import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import lombok.*;

import java.util.List;

@Builder
@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
public class MentorDashboardResponse {

    private DashboardCardsResponse cards;

    private PageResponse<ReviewsRsponse> reviews;

    private PageResponse<DashboardSessionResponse> sessions;

    private List<SalesChartResponse> salesChart;

    private PageResponse<NotificationDto> notifications;

}