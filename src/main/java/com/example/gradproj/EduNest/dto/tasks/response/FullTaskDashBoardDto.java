package com.example.gradproj.EduNest.dto.tasks.response;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FullTaskDashBoardDto {
    private TaskDashboardDTO taskDashboardDTO;
    private PageResponse<TaskResponse> taskResponsePageResponse;
}
