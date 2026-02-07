package com.example.gradproj.EduNest.dto.projects.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectResponse {
    private Long id;
    private String title;
    private String description;
    private Integer points;
    private Integer passPoints;
    private Integer estimatedMinutes;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // for unify the date formate
    private LocalDateTime dueAt;
    private String attachmentUrl;
}
