package com.example.gradproj.EduNest.service.quiz.submission;

import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EB-53 — exact grace boundary for quiz submissions.
 *
 * <p>The rejection decision compares exact timestamps
 * (now.isAfter(endDate.plusMinutes(grace))), so the boundary itself is
 * inclusive and even one second beyond it is rejected — partial minutes
 * no longer sneak past the grace. {@code ChronoUnit.MINUTES} is kept for
 * the -1 pt/minute penalty. Time is pinned via a static mock of
 * {@link LocalDateTime#now()} so the boundary cases are deterministic.
 */
class QuizSubmissionGraceBoundaryTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 12, 0, 0);
    private static final int GRACE_MINUTES = 10; // LateSubmissionProperties default

    private static final String STUDENT_EMAIL = "boundary-student@edunest.org";
    private static final Long QUIZ_ID = 1L;

    private static final Map<Long, UUID> QUESTION_UUIDS = new ConcurrentHashMap<>();

    private QuizRepository quizRepository;
    private QuizSubmissionRepository quizSubmissionRepository;
    private StudentRepository studentRepository;
    private TotalPointsServiceImp totalPointsService;
    private NotificationService notificationService;
    private SecurityService securityService;
    private QuizSubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        quizRepository = mock(QuizRepository.class);
        quizSubmissionRepository = mock(QuizSubmissionRepository.class);
        studentRepository = mock(StudentRepository.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        notificationService = mock(NotificationService.class);
        securityService = mock(SecurityService.class);

        service = new QuizSubmissionServiceImpl(
                quizRepository, quizSubmissionRepository, studentRepository,
                totalPointsService, notificationService, securityService,
                new LateSubmissionProperties());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static UUID uuidFor(long id) {
        return QUESTION_UUIDS.computeIfAbsent(id, k -> UUID.randomUUID());
    }

    private static Question question(long id, AnswerChoices correct, int points) {
        Question q = Question.builder().correctAnswer(correct).points(points).build();
        q.setId(id);
        q.setPublicId(uuidFor(id));
        return q;
    }

    private static StudentAnswerDTO answer(long questionId, String selected) {
        return StudentAnswerDTO.builder().questionId(uuidFor(questionId)).selectedAnswer(selected).build();
    }

    private Quiz quizWith(Question... questions) {
        Mentor mentor = Mentor.builder().email("mentor@test.com").build();
        MentorShip mentorship = MentorShip.builder().id(100L).title("Boundary Track").mentor(mentor).build();
        Week week = Week.builder().id(10L).mentorship(mentorship).build();
        return Quiz.builder()
                .id(QUIZ_ID)
                .publicId(UUID.randomUUID())
                .title("Boundary Quiz")
                .status(QuizStatus.PUBLISHED)
                .week(week)
                .questions(List.of(questions))
                .build();
    }

    private QuizSubmission submissionWithEnd(Quiz quiz, Student student, LocalDateTime endDate) {
        return QuizSubmission.builder()
                .id(9L).quiz(quiz).student(student).score(null)
                .startDate(endDate.minusMinutes(30))
                .endDate(endDate)
                .answers(new ArrayList<>())
                .build();
    }

    private void stubHappyPath(Quiz quiz, Student student, QuizSubmission submission) {
        when(securityService.getCurrentUserEmail()).thenReturn(STUDENT_EMAIL);
        when(securityService.isStudentEnrolledByQuizId(STUDENT_EMAIL, QUIZ_ID)).thenReturn(true);
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(quizRepository.findForUpdateById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(studentRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(quizSubmissionRepository.findByStudentIdAndQuizIdForUpdate(student.getId(), QUIZ_ID))
                .thenReturn(Optional.of(submission));
    }

    private static final QuizSubmissionDTO CORRECT_ANSWERS = QuizSubmissionDTO.builder()
            .answers(List.of(answer(1L, "A"), answer(2L, "B")))
            .build();

    private QuizSubmissionResponseDTO submitInsidePinnedClock(Quiz quiz, Student student, QuizSubmission submission) {
        stubHappyPath(quiz, student, submission);
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            return service.submitQuizAnswers(CORRECT_ANSWERS, QUIZ_ID);
        }
    }

    private void expectRejectionBeforeMutation(Quiz quiz, Student student, QuizSubmission submission) {
        stubHappyPath(quiz, student, submission);
        try (MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(FIXED_NOW);
            assertThatThrownBy(() -> service.submitQuizAnswers(CORRECT_ANSWERS, QUIZ_ID))
                    .isInstanceOf(com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx.class)
                    .hasMessageContaining("window has closed");
        }
        // rejection happens BEFORE any mutation — answers, score, points and notifications untouched
        verify(quizSubmissionRepository, never()).save(any(QuizSubmission.class));
        verify(totalPointsService, never()).applyDelta(any(), any(), anyInt());
        verify(notificationService, never()).sendToUserByEmail(anyString(), anyString(), anyString(), any(NotificationType.class));
        assertThat(submission.getScore()).isNull();
        assertThat(submission.getIsLate()).isNull();
        assertThat(submission.getAnswers()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Boundary matrix (two 10-point questions -> raw score 20)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Submission exactly at the attempt deadline is accepted, not flagged late, full score")
    void exactlyAtDeadlineAccepted() {
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 10), question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email(STUDENT_EMAIL).build();
        QuizSubmission submission = submissionWithEnd(quiz, student, FIXED_NOW);

        QuizSubmissionResponseDTO resp = submitInsidePinnedClock(quiz, student, submission);

        assertThat(resp.getScore()).isEqualTo(20.0);
        assertThat(resp.getIsLate()).isFalse();
        verify(totalPointsService).applyDelta(student, quiz.getWeek().getMentorship(), 20);
    }

    @Test
    @DisplayName("Submission exactly at deadline + grace (the boundary itself) is accepted, late with -10 penalty")
    void exactlyAtDeadlinePlusGraceAccepted() {
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 10), question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email(STUDENT_EMAIL).build();
        QuizSubmission submission = submissionWithEnd(quiz, student, FIXED_NOW.minusMinutes(GRACE_MINUTES));

        QuizSubmissionResponseDTO resp = submitInsidePinnedClock(quiz, student, submission);

        assertThat(resp.getScore()).isEqualTo(10.0); // 20 raw - 10 late minutes
        assertThat(resp.getIsLate()).isTrue();
        verify(totalPointsService).applyDelta(student, quiz.getWeek().getMentorship(), 10);
    }

    @Test
    @DisplayName("Submission one second beyond deadline + grace is rejected before any mutation")
    void oneSecondBeyondGraceRejected() {
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 10), question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email(STUDENT_EMAIL).build();
        QuizSubmission submission = submissionWithEnd(
                quiz, student, FIXED_NOW.minusMinutes(GRACE_MINUTES).minusSeconds(1));

        expectRejectionBeforeMutation(quiz, student, submission);
    }

    @Test
    @DisplayName("A partial minute beyond the grace is rejected (regression: truncated minutesLate no longer accepts it)")
    void partialMinuteBeyondGraceRejected() {
        // 10 min + 30 s late: old ChronoUnit.MINUTES truncation computed minutesLate == 10 and accepted this.
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 10), question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email(STUDENT_EMAIL).build();
        QuizSubmission submission = submissionWithEnd(quiz, student, FIXED_NOW.minusSeconds(630));

        expectRejectionBeforeMutation(quiz, student, submission);
    }

    @Test
    @DisplayName("Inside the grace the penalty stays -1 pt per truncated late minute and isLate is set")
    void insideGracePenaltyAndIsLateCorrect() {
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 10), question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email(STUDENT_EMAIL).build();
        // 3 min + 30 s late -> 3 truncated late minutes, well inside the 10-minute grace
        QuizSubmission submission = submissionWithEnd(quiz, student, FIXED_NOW.minusSeconds(210));

        QuizSubmissionResponseDTO resp = submitInsidePinnedClock(quiz, student, submission);

        assertThat(resp.getScore()).isEqualTo(17.0); // 20 raw - 3 late minutes
        assertThat(resp.getIsLate()).isTrue();
        verify(totalPointsService, times(1)).applyDelta(student, quiz.getWeek().getMentorship(), 17);
    }
}
