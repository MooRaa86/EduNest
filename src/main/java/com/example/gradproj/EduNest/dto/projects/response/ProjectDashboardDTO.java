package com.example.gradproj.EduNest.dto.projects.response;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectDashboardDTO {
    private int totalProjects;
    private int publishedCount;
    private int draftCount;
    private double averageScore;
}

