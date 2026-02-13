package com.example.gradproj.EduNest.service.quiz.submission;

import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;

import java.util.List;

public interface QuizSubmissionService {

    QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, Long quizId);

    List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId, int page, int size);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId, int page, int size);


}
