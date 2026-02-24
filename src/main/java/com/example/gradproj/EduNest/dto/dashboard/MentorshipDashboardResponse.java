package com.example.gradproj.EduNest.dto.dashboard;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipStatsResponse;
import com.example.gradproj.EduNest.repository.points.projection.TopStudentResponse;
import lombok.*;

@Setter @Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorshipDashboardResponse {

    private MentorshipStatsResponse stats;

    private PageResponse<ReviewsRsponse> reviews;

    private PageResponse<TopStudentResponse> topLearners;
}