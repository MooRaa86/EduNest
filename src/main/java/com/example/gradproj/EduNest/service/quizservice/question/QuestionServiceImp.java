package com.example.gradproj.EduNest.service.quizservice.question;

import com.example.gradproj.EduNest.dto.quizdto.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quizdto.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.entity.quizentity.Question;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
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
    public QuestionResponseDTO createQuestion(QuestionCreateDTO questionCreateDTO) {
        Quiz quiz = quizRepository.findById(questionCreateDTO.getQuizId())
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new globalLogicEx("Cannot add question. Quiz is already published or closed.");
        }

        Question question = Question.builder()
                .quiz(quiz)
                .text(questionCreateDTO.getText())
                .points(questionCreateDTO.getPoints())
                .correctAnswer(questionCreateDTO.getCorrectAnswer())
                .optionA(questionCreateDTO.getOptionA())
                .optionB(questionCreateDTO.getOptionB())
                .optionC(questionCreateDTO.getOptionC())
                .optionD(questionCreateDTO.getOptionD())
                .build();

        Question saved = questionRepository.save(question);
        return mapToResponseDTO(saved);
    }

    @Override
    public List<QuestionResponseDTO> getQuestionsByQuizId(Long quizId) {
        quizRepository.findById(quizId).orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return questionRepository.findByQuiz_Id(quizId).stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    @Override
    public QuestionResponseDTO getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Question not found"));
        return mapToResponseDTO(question);
    }

    @Override
    public QuestionResponseDTO updateQuestion(Long id, QuestionUpdateDto dto) {

        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Question not found"));

        Quiz quiz = quizRepository.findById(dto.getQuizId())
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new globalLogicEx("Cannot update question. Quiz is already published or closed.");
        }

        if (dto.getText() != null)
            question.setText(dto.getText());

        if (dto.getPoints() != null)
            question.setPoints(dto.getPoints());

        if (dto.getCorrectAnswer() != null)
            question.setCorrectAnswer(dto.getCorrectAnswer());

        if (dto.getOptionA() != null)
            question.setOptionA(dto.getOptionA());

        if (dto.getOptionB() != null)
            question.setOptionB(dto.getOptionB());

        if (dto.getOptionC() != null)
            question.setOptionC(dto.getOptionC());

        if (dto.getOptionD() != null)
            question.setOptionD(dto.getOptionD());

        if (!question.getQuiz().getId().equals(dto.getQuizId())) {
            question.setQuiz(quiz);
        }

        Question updated = questionRepository.save(question);
        return mapToResponseDTO(updated);
    }

    @Override
    public void deleteQuestion(Long quizId, Long questionId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (!quiz.getStatus().equals(QuizStatus.DRAFT)) {
            throw new globalLogicEx("Cannot delete question. Quiz is already published or closed.");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new globalLogicEx("Question not found"));

        if (!question.getQuiz().getId().equals(quizId)) {
            throw new globalLogicEx("Question does not belong to this quiz");
        }


        questionRepository.delete(question);
    }

    private QuestionResponseDTO mapToResponseDTO(Question question) {
        return QuestionResponseDTO.builder()
                .id(question.getId())
                .text(question.getText())
                .points(question.getPoints())
                .correctAnswer(question.getCorrectAnswer())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .build();
    }
}
