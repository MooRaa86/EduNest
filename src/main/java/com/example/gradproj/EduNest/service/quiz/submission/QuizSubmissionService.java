package com.example.gradproj.EduNest.service.quiz.submission;

import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.dto.quiz.response.StudentQuizReviewDTO;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface QuizSubmissionService {

    @PreAuthorize("hasRole('STUDENT')")
    QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, Long quizId);

    List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId);

    List<StudentQuizReviewDTO> getStudentQuizReview(Long quizId);

    @PreAuthorize("hasRole('MENTOR')")
    List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size);


    @PreAuthorize("hasRole('STUDENT')")
    QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, UUID quizId);

    List<StudentAnswerDTO> getStudentAnswers(UUID studentId, UUID quizId);

    List<StudentQuizReviewDTO> getStudentQuizReview(UUID quizId);

    @PreAuthorize("hasRole('MENTOR')")
    List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(UUID quizId, int page, int size);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(UUID studentId, int page, int size);
}
