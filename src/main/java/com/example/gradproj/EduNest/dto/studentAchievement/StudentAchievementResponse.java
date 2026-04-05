package com.example.gradproj.EduNest.dto.studentAchievement;

import lombok.Builder;
import lombok.Data;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;

import java.util.List;

@Data
@Builder
public class StudentAchievementResponse {
    private PageResponse<BadgeAchievementResponse> badges;
    private PageResponse<ProjectAchievementResponse> projectSubmissions;
}
