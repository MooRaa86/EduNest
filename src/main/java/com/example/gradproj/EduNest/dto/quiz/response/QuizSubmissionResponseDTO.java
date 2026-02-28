package com.example.gradproj.EduNest.dto.quiz.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuizSubmissionResponseDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private Double score;
    private String status;
}