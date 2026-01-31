package com.example.gradproj.EduNest.dto.tasks.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskDashboardDTO {
    private int totalTasks;
    private int publishedCount;
    private int draftCount;
    private double averageScore;
}
