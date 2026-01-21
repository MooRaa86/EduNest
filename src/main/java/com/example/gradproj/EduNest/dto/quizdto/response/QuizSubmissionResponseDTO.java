package com.example.gradproj.EduNest.dto.quizdto.response;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
public class QuizSubmissionResponseDTO {

    @NotNull
    private Long id;

    @NotNull
    private Long studentId;

    @NotNull
    private Long quizId;

    @Min(0)
    private Integer score;

    @NotNull
    private LocalDateTime submittedAt;
}
