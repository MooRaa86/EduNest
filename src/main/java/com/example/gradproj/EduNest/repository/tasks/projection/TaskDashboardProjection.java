package com.example.gradproj.EduNest.repository.tasks.projection;

public interface TaskDashboardProjection {
    Long getTotalTasks();
    Long getPublishedCount();
    Long getDraftCount();
    Double getAverageScore();
}
