package com.example.gradproj.EduNest.dto.projects.request;


import com.example.gradproj.EduNest.enums.project.ProjectDifficultyLevel;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatchProjectRequest {

    @Size(min = 3, max = 150, message = "title must be between 3 and 150 characters")
    private String title;

    @Size(min = 5, max = 255, message = "goal must be between 5 and 255 characters")
    private String goal;

//    private ProjectDifficultyLevel difficulty;

    @Size(min = 10, max = 10000, message = "brief must be between 10 and 10000 characters")
    private String brief;

    @Size(max = 1000, message = "descriptionUrl max length is 1000")
    @URL(message = "descriptionUrl must be a valid URL")
    private String descriptionUrl;

    @FutureOrPresent(message = "startAt must be now or in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @Future(message = "endAt must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    @Min(value = 1, message = "points must be >= 1")
    @Max(value = 1000, message = "points is too large")
    private Integer points;


    private ProjectStatus status;
}
