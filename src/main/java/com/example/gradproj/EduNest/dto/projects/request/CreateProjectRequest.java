package com.example.gradproj.EduNest.dto.projects.request;

import com.example.gradproj.EduNest.enums.project.ProjectStatus;
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

    @NotNull(message = "weekId is required")
    private Long weekId;

    @NotBlank(message = "goal is required")
    @Size(min = 5, max = 255, message = "goal must be between 5 and 255 characters")
    private String goal;

//    @NotNull(message = "difficulty is required")
//    private ProjectDifficultyLevel difficulty;

    @NotBlank(message = "brief is required")
    @Size(min = 10, max = 10000, message = "brief must be between 10 and 10000 characters")
    private String brief;

    @Size(max = 1000, message = "descriptionUrl max length is 1000")
    @URL(message = "descriptionUrl must be a valid URL")
    private String descriptionUrl;

    @NotNull(message = "startAt is required")
    @FutureOrPresent(message = "startAt must be now or in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @NotNull(message = "endAt is required")
    @Future(message = "endAt must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    @NotNull(message = "points is required")
    @Min(value = 1, message = "points must be >= 1")
    @Max(value = 1000, message = "points is too large")
    private Integer points;

    @NotBlank(message = "status is required")
    private ProjectStatus status;
}
