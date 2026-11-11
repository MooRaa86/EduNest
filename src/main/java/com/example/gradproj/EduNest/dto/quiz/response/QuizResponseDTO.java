package com.example.gradproj.EduNest.dto.quiz.response;

import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizResponseDTO {

    private UUID id;
    private String title;
    private Integer durationMinutes;
    private String description;
    private QuizStatus status;
    private int submissions;
    private double averageScore;
    private int totalPoints;
    /** EB-53 / D-08 — additive: absolute close deadline (null on legacy quizzes = manual close only). */
    private LocalDateTime closeAt;
}
