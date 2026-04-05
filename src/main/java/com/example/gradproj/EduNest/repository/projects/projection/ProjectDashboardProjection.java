package com.example.gradproj.EduNest.repository.projects.projection;

public interface ProjectDashboardProjection {
    Long getTotalProjects();
    Long getPublishedCount();
    Long getDraftCount();
    Double getAverageScore();
}
