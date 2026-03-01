package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quiz.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.MentorshipQuizzesOverviewResponseDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuizOverviewResponseDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

public interface QuizService {
    @PreAuthorize("hasRole('MENTOR')")
    QuizResponseDTO createQuiz(QuizCreateDTO quizCreateDTO);

    @PreAuthorize("hasRole('MENTOR')")
    void deleteQuiz(Long id);

    @PreAuthorize("hasRole('MENTOR')")
    QuizResponseDTO updateQuiz(Long id, QuizUpdateDto quizUpdateDto);

    QuizResponseDTO getQuizDetails(Long id);

    PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status,Long msid ,Pageable pageable);

    @PreAuthorize("hasRole('MENTOR')")
    QuizDashboardDTO getQuizDashboard(Long mentorshipID);

    @PreAuthorize("hasRole('MENTOR')")
    QuizStatisticsDTO getQuizStatistics(Long quizId);
    @PreAuthorize("hasRole('MENTOR')")
    QuizOverviewResponseDto getQuizOverviewDto(Long quizId, int page, int size);

    @PreAuthorize("hasRole('MENTOR')")
    void changeStatus(Long quizId, QuizStatus quizStatus);
    @PreAuthorize("hasRole('MENTOR')")
    MentorshipQuizzesOverviewResponseDto getMentorshipQuizzesOverview(Long mentorShipId, int page, int size);
}