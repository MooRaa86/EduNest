package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.dto.quiz.request.QuizUpdateDto;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuizLifecycleGuardTest {

    private QuizRepository quizRepository;
    private MentorShipRepository mentorshipRepository;
    private EnrollmentRepository enrollmentRepository;
    private WeekRepository weekRepository;
    private QuizSubmissionService quizSubmissionService;
    private NotificationService notificationService;
    private SecurityService securityService;
    private QuizSubmissionRepository quizSubmissionRepository;
    private QuizServiceImpl quizService;

    private static final String MENTOR_EMAIL = "mentor@test.org";
    private static final Long QUIZ_ID = 42L;
    private static final Long WEEK_ID = 7L;

    @BeforeEach
    void setUp() {
        quizRepository = mock(QuizRepository.class);
        mentorshipRepository = mock(MentorShipRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        weekRepository = mock(WeekRepository.class);
        quizSubmissionService = mock(QuizSubmissionService.class);
        notificationService = mock(NotificationService.class);
        securityService = mock(SecurityService.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);

        quizService = new QuizServiceImpl(
                quizRepository,
                mentorshipRepository,
                enrollmentRepository,
                weekRepository,
                quizSubmissionService,
                notificationService,
                securityService,
                quizSubmissionRepository
        );

        when(securityService.getCurrentUserEmail()).thenReturn(MENTOR_EMAIL);
        when(securityService.isMentorOwnQuiz(QUIZ_ID, MENTOR_EMAIL)).thenReturn(true);

        MentorShip mentorship = MentorShip.builder().id(100L).build();
        Week week = Week.builder().id(WEEK_ID).mentorship(mentorship).build();
        Quiz quiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.DRAFT).build();

        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(weekRepository.findForUpdateById(WEEK_ID)).thenReturn(Optional.of(week));
    }

    @Test
    @DisplayName("rejects deleteQuiz when scored quiz submissions exist")
    void rejectsDeleteWhenScoredSubmissionsExist() {
        when(quizSubmissionRepository.existsByQuiz_IdAndScoreIsNotNull(QUIZ_ID)).thenReturn(true);

        assertThatThrownBy(() -> quizService.deleteQuiz(QUIZ_ID))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot delete a quiz that has scored submissions");

        verify(quizRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("allows deleteQuiz when no scored submissions exist")
    void allowsDeleteWhenNoScoredSubmissionsExist() {
        when(quizSubmissionRepository.existsByQuiz_IdAndScoreIsNotNull(QUIZ_ID)).thenReturn(false);

        assertThatCode(() -> quizService.deleteQuiz(QUIZ_ID)).doesNotThrowAnyException();

        verify(weekRepository).findForUpdateById(WEEK_ID);
        verify(quizRepository).findForUpdateById(QUIZ_ID);
        verify(quizRepository).deleteById(QUIZ_ID);
    }

    @Test
    @DisplayName("rejects deleteQuiz when caller is not the mentor owner")
    void rejectsDeleteWhenNotOwner() {
        when(securityService.isMentorOwnQuiz(QUIZ_ID, MENTOR_EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> quizService.deleteQuiz(QUIZ_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not authorized to delete this quiz");

        verify(quizRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("rejects updateQuiz when quiz status is PUBLISHED")
    void rejectsUpdateWhenPublished() {
        Week week = Week.builder().id(WEEK_ID).build();
        Quiz publishedQuiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.PUBLISHED).build();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(publishedQuiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(publishedQuiz));

        QuizUpdateDto dto = new QuizUpdateDto();
        dto.setTitle("Updated Title");

        assertThatThrownBy(() -> quizService.updateQuiz(QUIZ_ID, dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot update published or closed quiz");
    }

    @Test
    @DisplayName("rejects updateQuiz when quiz status is CLOSED")
    void rejectsUpdateWhenClosed() {
        Week week = Week.builder().id(WEEK_ID).build();
        Quiz closedQuiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.CLOSED).build();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(closedQuiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(closedQuiz));

        QuizUpdateDto dto = new QuizUpdateDto();
        dto.setTitle("Updated Title");

        assertThatThrownBy(() -> quizService.updateQuiz(QUIZ_ID, dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot update published or closed quiz");
    }

    @Test
    @DisplayName("rejects changeStatus when quiz is CLOSED")
    void rejectsChangeStatusWhenClosed() {
        Week week = Week.builder().id(WEEK_ID).build();
        Quiz closedQuiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.CLOSED).build();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(closedQuiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(closedQuiz));

        assertThatThrownBy(() -> quizService.changeStatus(QUIZ_ID, QuizStatus.PUBLISHED))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot change status of a closed quiz");
    }

    @Test
    @DisplayName("rejects changeStatus to DRAFT when submissions exist")
    void rejectsRevertingToDraftWithSubmissions() {
        Week week = Week.builder().id(WEEK_ID).build();
        Quiz publishedQuiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.PUBLISHED).build();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(publishedQuiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(publishedQuiz));
        when(quizSubmissionRepository.existsByQuiz_Id(QUIZ_ID)).thenReturn(true);

        assertThatThrownBy(() -> quizService.changeStatus(QUIZ_ID, QuizStatus.DRAFT))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot revert quiz with submissions to draft");
    }

    @Test
    @DisplayName("rejects changeStatus to PUBLISHED when quiz has no questions")
    void rejectsPublishingQuizWithoutQuestions() {
        Week week = Week.builder().id(WEEK_ID).build();
        Quiz draftQuiz = Quiz.builder().id(QUIZ_ID).week(week).status(QuizStatus.DRAFT).questions(Collections.emptyList()).build();
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(draftQuiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(draftQuiz));

        assertThatThrownBy(() -> quizService.changeStatus(QUIZ_ID, QuizStatus.PUBLISHED))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Cannot publish a quiz with no questions");
    }
}
