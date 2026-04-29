package com.example.gradproj.EduNest.dto.admin;

import com.example.gradproj.EduNest.dto.dashboard.UsersChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.repository.users.projection.UserListProjection;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminDashboardSummaryResponse {
        private List<UsersChartResponse> monthlyUsers;
        private PageResponse<UserListProjection> allUsersPaginated;
}
