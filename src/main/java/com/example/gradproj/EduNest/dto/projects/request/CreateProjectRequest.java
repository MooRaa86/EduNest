package com.example.gradproj.EduNest.dto.projects.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CreateProjectRequest {
    @NotBlank(message = "title is required")
    @Size(min = 3, max = 150, message = "title must be between 3 and 150 characters")
    private String title;

    @NotNull
    private Long mentorshipId;

    @NotBlank(message = "description is required")
    @Size(min = 10, max = 10000, message = "description must be between 10 and 10000 characters")
    private String description;

    @NotNull(message = "points is required")
    @Min(value = 0, message = "points must be >= 0")
    @Max(value = 1000, message = "points is too large")
    private Integer points;

    @NotNull(message = "passPoints is required")
    @Min(value = 0, message = "passPoints must be >= 0")
    @Max(value = 1000, message = "passPoints is too large")
    private Integer passPoints;

    @NotNull(message = "estimatedMinutes is required")
    @Min(value = 1, message = "estimatedMinutes must be >= 1")
    @Max(value = 100000, message = "estimatedMinutes is too large")
    private Integer estimatedMinutes;

    @NotNull(message = "dueAt is required")
    @Future(message = "dueAt must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // for unify the date formate
    private LocalDateTime dueAt;

    @Size(max = 500, message = "attachmentUrl max length is 500")
    @URL(message = "attachmentUrl must be a valid URL")
    private String attachmentUrl;
}
