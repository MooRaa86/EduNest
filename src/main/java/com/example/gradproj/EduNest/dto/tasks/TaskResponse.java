package com.example.gradproj.EduNest.dto.tasks;

import lombok.*;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private Integer points;
    private Integer passPoints;
    private Integer estimatedMinutes;
    private Integer maxAttempts;
    private String status;
    private LocalDateTime dueAt;
    private String attachmentUrl;
}
