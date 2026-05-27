package com.example.gradproj.EduNest.service.quiz.question;

import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.dto.quiz.response.StudentQuestionResponseDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

public interface QuestionService {

    @PreAuthorize("hasRole('MENTOR')")
    QuestionResponseDTO createQuestion(QuestionCreateDTO questionCreateDTO);

    List<QuestionResponseDTO> getQuestionsByQuizId(Long quizId);

    @PreAuthorize("hasRole('STUDENT')")
    List<StudentQuestionResponseDTO> getQuestionsByQuizIdForStudent(Long quizId);

    QuestionResponseDTO getQuestionById(Long id);

    @PreAuthorize("hasRole('MENTOR')")
    QuestionResponseDTO updateQuestion(Long id, QuestionUpdateDto dto);

    @PreAuthorize("hasRole('MENTOR')")
     void deleteQuestion(Long quizId, Long questionId);
}
