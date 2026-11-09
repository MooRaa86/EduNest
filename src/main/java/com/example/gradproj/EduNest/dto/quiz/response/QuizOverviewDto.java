package com.example.gradproj.EduNest.dto.quiz.response;

import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizOverviewDto {
    private UUID id;
    private String title;
    private QuizStatus status;
    private int submissions;
    private double averageScore;
}