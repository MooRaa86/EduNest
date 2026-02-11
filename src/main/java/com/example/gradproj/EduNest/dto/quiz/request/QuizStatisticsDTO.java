package com.example.gradproj.EduNest.dto.quiz.request;

import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizStatisticsDTO {
    private QuizStatus status;
    private int totalStudents;
    private int totalSubmissions;
    private double averageScore;
    private int totalQuestions;
    private int totalPoints;
}
