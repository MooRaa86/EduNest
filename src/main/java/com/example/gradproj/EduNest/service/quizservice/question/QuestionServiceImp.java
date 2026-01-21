package com.example.gradproj.EduNest.service.quizservice.question;

import com.example.gradproj.EduNest.dto.quizdto.request.QuestionDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.entity.quizentity.Question;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.quizrepository.QuestionRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionServiceImp implements QuestionService {

    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;

    @Override
    public QuestionResponseDTO createQuestion(QuestionDTO questionDTO) {
        Quiz quiz = quizRepository.findById(questionDTO.getQuizId())
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        Question question = Question.builder()
                .quiz(quiz)
                .text(questionDTO.getText())
                .points(questionDTO.getPoints())
                .orderNumber(questionDTO.getOrderNumber())
                .correctAnswer(questionDTO.getCorrectAnswer())
                .optionA(questionDTO.getOptionA())
                .optionB(questionDTO.getOptionB())
                .optionC(questionDTO.getOptionC())
                .optionD(questionDTO.getOptionD())
                .build();

        Question saved = questionRepository.save(question);
        return mapToResponseDTO(saved);
    }

    @Override
    public List<QuestionResponseDTO> getQuestionsByQuizId(Long quizId) {
        return questionRepository.findAll().stream()
                .filter(q -> q.getQuiz().getId().equals(quizId))
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public QuestionResponseDTO getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Question not found"));
        return mapToResponseDTO(question);
    }

    @Override
    public QuestionResponseDTO updateQuestion(Long id, QuestionDTO questionDTO) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Question not found"));

        Quiz quiz = quizRepository.findById(questionDTO.getQuizId())
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        question.setQuiz(quiz);
        question.setText(questionDTO.getText());
        question.setPoints(questionDTO.getPoints());
        question.setOrderNumber(questionDTO.getOrderNumber());
        question.setCorrectAnswer(questionDTO.getCorrectAnswer());
        question.setOptionA(questionDTO.getOptionA());
        question.setOptionB(questionDTO.getOptionB());
        question.setOptionC(questionDTO.getOptionC());
        question.setOptionD(questionDTO.getOptionD());

        Question updated = questionRepository.save(question);
        return mapToResponseDTO(updated);
    }

    @Override
    public void deleteQuestion(Long id) {
        questionRepository.deleteById(id);
    }

    private QuestionResponseDTO mapToResponseDTO(Question question) {
        return QuestionResponseDTO.builder()
                .id(question.getId())
                .text(question.getText())
                .points(question.getPoints())
                .orderNumber(question.getOrderNumber())
                .correctAnswer(question.getCorrectAnswer())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .build();
    }
}
