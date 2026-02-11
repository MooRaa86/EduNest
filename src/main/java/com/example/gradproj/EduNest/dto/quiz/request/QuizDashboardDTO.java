package com.example.gradproj.EduNest.dto.quiz.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizDashboardDTO {

    private int totalQuizzes;
    private int publishedCount;
    private int draftCount;
    private double averageScore;
}
