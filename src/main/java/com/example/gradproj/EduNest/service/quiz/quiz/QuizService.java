package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import org.springframework.data.domain.Pageable;

public interface QuizService {
    QuizResponseDTO createQuiz(QuizCreateDTO quizCreateDTO);

    void deleteQuiz(Long id);

    QuizResponseDTO updateQuiz(Long id, QuizUpdateDto quizUpdateDto);

    QuizResponseDTO getQuizDetails(Long id);

    PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status,Long msid ,Pageable pageable);

    QuizDashboardDTO getQuizDashboard(Long mentorshipID);

    QuizStatisticsDTO getQuizStatistics(Long quizId);

    void changeStatus(Long quizId, QuizStatus quizStatus);
}
