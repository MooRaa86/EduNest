package com.example.gradproj.EduNest.dto.quiz.request;

import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class QuizCreateDTO {

    @NotNull(message = "Week ID is required")
    private UUID weekId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title can't exceed 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description can't exceed 500 characters")
    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    /**
     * EB-53 / D-08 — absolute close deadline. Required (and must be in the
     * future) when the quiz is created directly in PUBLISHED status; optional
     * while DRAFT.
     */
    private LocalDateTime closeAt;

    private QuizStatus status = QuizStatus.DRAFT;

}
