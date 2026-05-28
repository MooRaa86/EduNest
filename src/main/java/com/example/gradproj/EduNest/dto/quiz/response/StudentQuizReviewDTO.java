package com.example.gradproj.EduNest.dto.quiz.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentQuizReviewDTO {
    private Long questionId;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
    private String selectedAnswer;
}
