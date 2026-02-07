package com.example.gradproj.EduNest.dto.projects.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GradeProjectSubmissionRequest {
    @NotNull(message = "score is required")
    @Min(value = 0, message = "score must be >= 0")
    @Max(value = 1000, message = "score is too large")
    private Integer score;

    @Size(max = 5000, message = "feedback max length is 5000")
    private String feedback;
}
