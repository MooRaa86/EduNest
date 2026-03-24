package com.example.gradproj.EduNest.dto.projects.response;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FullProjectDashBoardDto {
    private ProjectDashboardDTO projectDashboardDTO;
    private PageResponse<ProjectWithStatsResponse> projectResponsePageResponse;
}
