package com.example.gradproj.EduNest.dto.projects.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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


    @Size(min = 10, max = 10000, message = "description must be between 10 and 10000 characters")
    private String description;


    @Min(value = 0, message = "points must be >= 0")
    @Max(value = 1000, message = "points is too large")
    private Integer points;


    @Min(value = 0, message = "passPoints must be >= 0")
    @Max(value = 1000, message = "passPoints is too large")
    private Integer passPoints;


    @Min(value = 1, message = "estimatedMinutes must be >= 1")
    @Max(value = 100000, message = "estimatedMinutes is too large")
    private Integer estimatedMinutes;


    @Future(message = "dueAt must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // for unify the date formate
    private LocalDateTime dueAt;

    @Size(max = 500, message = "attachmentUrl max length is 500")
    @URL(message = "attachmentUrl must be a valid URL")
    private String attachmentUrl;
}
