package com.example.gradproj.EduNest.dto.homepage;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class studentProgressDto {
    Long totalTasks;
    Long completedTasks;
    Long totalQuizzes;
    Long completedQuizzes;
    Long totalProjects;
    Long completedProjects;
    Integer progressPercentage;
}
