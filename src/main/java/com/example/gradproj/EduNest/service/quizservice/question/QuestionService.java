package com.example.gradproj.EduNest.service.quizservice.question;

import com.example.gradproj.EduNest.dto.quizdto.request.QuestionDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuestionResponseDTO;
import java.util.List;

public interface QuestionService {

    QuestionResponseDTO createQuestion(QuestionDTO questionDTO);

    List<QuestionResponseDTO> getQuestionsByQuizId(Long quizId);

    QuestionResponseDTO getQuestionById(Long id);

    QuestionResponseDTO updateQuestion(Long id, QuestionDTO questionDTO);

    void deleteQuestion(Long id);
}
