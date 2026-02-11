package com.example.gradproj.EduNest.dto.quiz.request;

import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class QuizCreateDTO {

    @NotNull(message = "Mentorship ID is required")
    private Long mentorshipId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title can't exceed 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description can't exceed 500 characters")
    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    private QuizStatus status = QuizStatus.DRAFT;

}
