package com.example.gradproj.EduNest.dto.projects.response;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectWithStatsResponse {
    private ProjectResponse project;
    private long totalStudents;
    private long submissionsCount;
}
