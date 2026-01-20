package com.example.gradproj.EduNest.dto.quizdto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuizSubmissionDTO {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Quiz ID is required")
    private Long quizId;

    @Min(value = 0, message = "Score cannot be negative")
    private Integer score;
}
