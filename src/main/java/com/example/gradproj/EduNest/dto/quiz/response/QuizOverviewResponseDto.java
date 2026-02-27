package com.example.gradproj.EduNest.dto.quiz.response;

import com.example.gradproj.EduNest.dto.quiz.request.QuizStatisticsDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuizOverviewResponseDto {
private QuizStatisticsDTO  quizStatistics;
private List<QuizSubmissionResponseDTO> submissions;

}
