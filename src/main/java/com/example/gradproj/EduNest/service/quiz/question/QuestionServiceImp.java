package com.example.gradproj.EduNest.service.quiz.question;

import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.QuestionResponseDTO;
import com.example.gradproj.EduNest.dto.quiz.response.StudentQuestionResponseDTO;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.service.quiz.quiz.QuizService;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionServiceImp implements QuestionService {

    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final SecurityService securityService;
    private final QuizService quizService;
    private final QuizSubmissionService quizSubmissionService;
    private final QuizSubmissionRepository quizSubmissionRepository;


    @Override
    public QuestionResponseDTO createQuestion(QuestionCreateDTO questionCreateDTO) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(questionCreateDTO.getQuizId(), email)) {
            throw new AccessDeniedException("You are not authorized to add questions to this quiz");
        }

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
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to view questions for this quiz");
        }

        if (!quizRepository.existsById(quizId)) {
            throw new globalLogicEx("Quiz not found");
        }
        return questionRepository.findByQuiz_Id(quizId).stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    @Override
    public List<StudentQuestionResponseDTO> getQuestionsByQuizIdForStudent(Long quizId) {
        String email = securityService.getCurrentUserEmail();

        if (!securityService.isStudentEnrolledByQuizId(email, quizId)) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new AccessDeniedException("Quiz is not published");
        }


        //testtttt
        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.setQuiz(quiz);
        quizSubmission.setStudent(securityService.getCurrentStudent());
        quizSubmission.setStartDate(LocalDateTime.now());
        quizSubmission.setEndDate(LocalDateTime.now().plusMinutes(quiz.getDurationMinutes()));
        quizSubmission.setStatus(SubmissionStatus.IN_PROGRESS);
        quizSubmissionRepository.save(quizSubmission);
        quizSubmissionService.scheduleQuizClose(quizSubmission);
        //teestttt


        return questionRepository.findByQuiz_Id(quizId).stream()
                .map(this::mapToStudentResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public QuestionResponseDTO getQuestionById(Long id) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuestion(id, email)) {
            throw new AccessDeniedException("You are not authorized to view this question");
        }

        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Question not found"));
        return mapToResponseDTO(question);
    }

    @Override
    public QuestionResponseDTO updateQuestion(Long id, QuestionUpdateDto dto) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuestion(id, email)) {
            throw new AccessDeniedException("You are not authorized to update this question");
        }

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
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuestion(questionId, email)) {
            throw new AccessDeniedException("You are not authorized to delete this question");
        }

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

    private StudentQuestionResponseDTO mapToStudentResponseDTO(Question question) {
        return StudentQuestionResponseDTO.builder()
                .id(question.getId())
                .text(question.getText())
                .points(question.getPoints())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .build();
    }
}
