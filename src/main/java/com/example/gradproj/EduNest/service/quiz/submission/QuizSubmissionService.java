package com.example.gradproj.EduNest.service.quiz.submission;

import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.dto.quiz.response.StudentQuizReviewDTO;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;

public interface QuizSubmissionService {

    @PreAuthorize("hasRole('STUDENT')")
    QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, Long quizId);

    List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId);

    List<StudentQuizReviewDTO> getStudentQuizReview(Long quizId);

    @PreAuthorize("hasRole('MENTOR')")
    List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size);

    void scheduleQuizClose(QuizSubmission quizSubmission);
    String checkSubmissionStatus(Long submissionId);


}
