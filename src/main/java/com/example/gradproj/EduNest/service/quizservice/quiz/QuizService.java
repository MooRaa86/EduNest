package com.example.gradproj.EduNest.service.quizservice.quiz;

import com.example.gradproj.EduNest.dto.quizdto.request.QuizDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.enums.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface QuizService {
    QuizResponseDTO createQuiz(QuizDTO quizdto);
    void deleteQuiz(Long id);
     QuizResponseDTO updateQuiz(Long id,QuizDTO quizdto);
    QuizResponseDTO getQuizDetails(Long id);
    Page<QuizResponseDTO> getQuizzes(
            String search,
            QuizStatus status,
            LocalDate deadline,
            Pageable pageable
    );
    QuizDashboardDTO getQuizDashboard();

    QuizStatisticsDTO getQuizStatistics(Long quizId);
    void publishQuiz(Long quizId);
    void closeQuiz(Long quizId);
}
