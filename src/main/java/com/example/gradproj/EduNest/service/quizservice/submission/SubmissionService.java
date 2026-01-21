package com.example.gradproj.EduNest.service.quizservice.submission;

import com.example.gradproj.EduNest.dto.quizdto.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizSubmissionResponseDTO;

import java.util.List;

public interface SubmissionService {

    QuizSubmissionResponseDTO submitQuizAnswers(QuizSubmissionDTO quizSubmissionDTO, Long quizId);

    List<StudentAnswerDTO> getStudentAnswers(Long studentId, Long quizId);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByStudent(Long studentId);

    List<QuizSubmissionResponseDTO> getAllSubmissionsByQuiz(Long quizId);

}
