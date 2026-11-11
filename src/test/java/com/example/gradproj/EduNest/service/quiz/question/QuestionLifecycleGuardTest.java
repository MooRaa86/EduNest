package com.example.gradproj.EduNest.service.quiz.question;

import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.quiz.quiz.QuizService;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuestionLifecycleGuardTest {

    private QuestionRepository questionRepository;
    private QuizRepository quizRepository;
    private SecurityService securityService;
    private QuizService quizService;
    private QuizSubmissionService quizSubmissionService;
    private QuizSubmissionRepository quizSubmissionRepository;
    private WeekRepository weekRepository;
    private QuestionServiceImp questionService;

    private static final String MENTOR_EMAIL = "mentor@test.org";
    private static final Long QUIZ_ID = 10L;
    private static final UUID QUIZ_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_QUIZ_PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long WEEK_ID = 5L;
    private static final Long QUESTION_ID = 100L;
    private static final UUID QUESTION_PUBLIC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        questionRepository = mock(QuestionRepository.class);
        quizRepository = mock(QuizRepository.class);
        securityService = mock(SecurityService.class);
        quizService = mock(QuizService.class);
        quizSubmissionService = mock(QuizSubmissionService.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);
        weekRepository = mock(WeekRepository.class);

        questionService = new QuestionServiceImp(
                questionRepository,
                quizRepository,
                securityService,
                quizService,
                quizSubmissionService,
                quizSubmissionRepository,
                weekRepository
        );

        when(securityService.getCurrentUserEmail()).thenReturn(MENTOR_EMAIL);
    }

    private Quiz buildQuiz(Long id, UUID publicId, Long weekId, QuizStatus status) {
        Week week = Week.builder().id(weekId).build();
        return Quiz.builder().id(id).publicId(publicId).week(week).status(status).title("Test Quiz").build();
    }

    private Question buildQuestion(Long id, UUID publicId, Quiz quiz) {
        Question q = Question.builder()
                .quiz(quiz)
                .text("Test Question")
                .points(5)
                .correctAnswer(AnswerChoices.A)
                .optionA("Option A")
                .optionB("Option B")
                .optionC("Option C")
                .optionD("Option D")
                .build();
        q.setId(id);
        q.setPublicId(publicId);
        return q;
    }

    @Test
    @DisplayName("createQuestion: rejects adding question when quiz is PUBLISHED")
    void createQuestion_rejectsWhenQuizPublished() {
        when(securityService.isMentorOwnQuiz(QUIZ_ID, MENTOR_EMAIL)).thenReturn(true);
        Quiz quiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.PUBLISHED);
        when(quizRepository.findByPublicId(QUIZ_PUBLIC_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(quiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));

        QuestionCreateDTO dto = QuestionCreateDTO.builder()
                .quizId(QUIZ_PUBLIC_ID)
                .text("Q1")
                .points(1)
                .correctAnswer(AnswerChoices.A)
                .optionA("A").optionB("B").optionC("C").optionD("D")
                .build();

        assertThatThrownBy(() -> questionService.createQuestion(dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot add question. Quiz is already published or closed.");
    }

    @Test
    @DisplayName("createQuestion: rejects adding question when quiz is CLOSED")
    void createQuestion_rejectsWhenQuizClosed() {
        when(securityService.isMentorOwnQuiz(QUIZ_ID, MENTOR_EMAIL)).thenReturn(true);
        Quiz quiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.CLOSED);
        when(quizRepository.findByPublicId(QUIZ_PUBLIC_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(quiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));

        QuestionCreateDTO dto = QuestionCreateDTO.builder()
                .quizId(QUIZ_PUBLIC_ID)
                .text("Q1")
                .points(1)
                .correctAnswer(AnswerChoices.A)
                .optionA("A").optionB("B").optionC("C").optionD("D")
                .build();

        assertThatThrownBy(() -> questionService.createQuestion(dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot add question. Quiz is already published or closed.");
    }

    @Test
    @DisplayName("updateQuestion: rejects updating question when source quiz is PUBLISHED")
    void updateQuestion_rejectsWhenSourceQuizPublished() {
        when(securityService.isMentorOwnQuestion(QUESTION_ID, MENTOR_EMAIL)).thenReturn(true);
        when(securityService.isMentorOwnQuiz(QUIZ_ID, MENTOR_EMAIL)).thenReturn(true);

        Quiz quiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.PUBLISHED);
        Question question = buildQuestion(QUESTION_ID, QUESTION_PUBLIC_ID, quiz);

        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(quizRepository.findByPublicId(QUIZ_PUBLIC_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(quiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(questionRepository.findForUpdateById(QUESTION_ID)).thenReturn(Optional.of(question));

        QuestionUpdateDto dto = QuestionUpdateDto.builder()
                .quizId(QUIZ_PUBLIC_ID)
                .text("Updated Text")
                .build();

        assertThatThrownBy(() -> questionService.updateQuestion(QUESTION_ID, dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot update question. Quiz is already published or closed.");
    }

    @Test
    @DisplayName("updateQuestion: rejects moving question when target quiz is CLOSED")
    void updateQuestion_rejectsWhenTargetQuizClosed() {
        when(securityService.isMentorOwnQuestion(QUESTION_ID, MENTOR_EMAIL)).thenReturn(true);
        Long targetQuizId = 20L;
        when(securityService.isMentorOwnQuiz(targetQuizId, MENTOR_EMAIL)).thenReturn(true);

        Quiz sourceQuiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.DRAFT);
        Quiz targetQuiz = buildQuiz(targetQuizId, TARGET_QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.CLOSED);
        Question question = buildQuestion(QUESTION_ID, QUESTION_PUBLIC_ID, sourceQuiz);

        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(quizRepository.findByPublicId(TARGET_QUIZ_PUBLIC_ID)).thenReturn(Optional.of(targetQuiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(sourceQuiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(sourceQuiz));
        when(quizRepository.findForUpdateById(targetQuizId)).thenReturn(Optional.of(targetQuiz));
        when(questionRepository.findForUpdateById(QUESTION_ID)).thenReturn(Optional.of(question));

        QuestionUpdateDto dto = QuestionUpdateDto.builder()
                .quizId(TARGET_QUIZ_PUBLIC_ID)
                .text("Moving Question")
                .build();

        assertThatThrownBy(() -> questionService.updateQuestion(QUESTION_ID, dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot move question. Target quiz is already published or closed.");
    }

    @Test
    @DisplayName("deleteQuestion: rejects deleting question when quiz is PUBLISHED")
    void deleteQuestion_rejectsWhenQuizPublished() {
        when(securityService.isMentorOwnQuestion(QUESTION_ID, MENTOR_EMAIL)).thenReturn(true);
        Quiz quiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.PUBLISHED);
        Question question = buildQuestion(QUESTION_ID, QUESTION_PUBLIC_ID, quiz);

        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(quiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(questionRepository.findForUpdateById(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionService.deleteQuestion(QUIZ_ID, QUESTION_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot delete question. Quiz is already published or closed.");
    }

    @Test
    @DisplayName("deleteQuestion: rejects deleting question when quiz is CLOSED")
    void deleteQuestion_rejectsWhenQuizClosed() {
        when(securityService.isMentorOwnQuestion(QUESTION_ID, MENTOR_EMAIL)).thenReturn(true);
        Quiz quiz = buildQuiz(QUIZ_ID, QUIZ_PUBLIC_ID, WEEK_ID, QuizStatus.CLOSED);
        Question question = buildQuestion(QUESTION_ID, QUESTION_PUBLIC_ID, quiz);

        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(quiz.getWeek()));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(questionRepository.findForUpdateById(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> questionService.deleteQuestion(QUIZ_ID, QUESTION_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Cannot delete question. Quiz is already published or closed.");
    }
}
