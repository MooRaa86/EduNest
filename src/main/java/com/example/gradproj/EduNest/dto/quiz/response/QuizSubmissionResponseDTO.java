package com.example.gradproj.EduNest.dto.quiz.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class QuizSubmissionResponseDTO {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private Double score;
    private int totalPoints;
    private String status;
}