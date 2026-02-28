package com.example.gradproj.EduNest.dto.quiz.response;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quiz.request.QuizDashboardDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorshipQuizzesOverviewResponseDto {
    private PageResponse<QuizOverviewDto> quizOverviewDtoPageResponse;
    private QuizDashboardDTO  quizDashboardDTO;
}