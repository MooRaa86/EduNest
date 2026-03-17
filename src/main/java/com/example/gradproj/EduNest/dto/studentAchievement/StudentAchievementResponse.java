package com.example.gradproj.EduNest.dto.studentAchievement;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentAchievementResponse {
    private List<BadgeAchievementResponse> badges;
    private List<ProjectAchievementResponse> projectSubmissions;
}
