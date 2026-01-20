package com.example.gradproj.EduNest.dto.quizdto.request;

import com.example.gradproj.EduNest.enums.QuizStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QuizDTO {

    @NotNull(message = "Mentorship ID is required")
    private Long mentorshipId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title can't exceed 100 characters")
    private String title;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    @NotNull(message = "Total points are required")
    @Min(value = 0, message = "Points can't be negative")
    private Integer totalPoints;

    private QuizStatus status = QuizStatus.DRAFT;

    @FutureOrPresent(message = "Deadline cannot be in the past")
    private LocalDate deadline;
}
