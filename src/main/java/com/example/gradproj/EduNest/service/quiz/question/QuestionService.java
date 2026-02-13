package com.example.gradproj.EduNest.service.quiz.question;

import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuestionResponseDTO;
import java.util.List;

public interface QuestionService {

    QuestionResponseDTO createQuestion(QuestionCreateDTO questionCreateDTO);

    List<QuestionResponseDTO> getQuestionsByQuizId(Long quizId);

    QuestionResponseDTO getQuestionById(Long id);

    QuestionResponseDTO updateQuestion(Long id, QuestionUpdateDto dto);

     void deleteQuestion(Long quizId, Long questionId);
}
