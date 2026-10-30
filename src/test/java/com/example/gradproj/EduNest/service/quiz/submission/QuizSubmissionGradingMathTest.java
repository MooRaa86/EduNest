package com.example.gradproj.EduNest.service.quiz.submission;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizSubmissionGradingMathTest {

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
                totalPointsService, notificationService, securityService);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------


    private QuizSubmissionServiceImpl.ScoreResult calculateScore(
            List<StudentAnswerDTO> answers, Map<Long, Question> questions) throws Exception {
        Method m = QuizSubmissionServiceImpl.class
                .getDeclaredMethod("calculateScore", List.class, Map.class);
        m.setAccessible(true);
        return (QuizSubmissionServiceImpl.ScoreResult) m.invoke(service, answers, questions);
    }

    private static Question question(long id, AnswerChoices correct, int points) {
        Question q = Question.builder().correctAnswer(correct).points(points).build();
        q.setId(id);
        return q;
    }

    private static StudentAnswerDTO answer(long questionId, String selected) {
        return StudentAnswerDTO.builder().questionId(questionId).selectedAnswer(selected).build();
    }

    private Quiz quizWith(Question... questions) {
        Mentor mentor = Mentor.builder().email("mentor@test.com").build();
        MentorShip mentorship = MentorShip.builder().id(100L).title("Backend Track").mentor(mentor).build();
        Week week = Week.builder().id(10L).mentorship(mentorship).build();
        return Quiz.builder()
                .id(1L)
                .title("Java Basics Quiz")
                .status(QuizStatus.PUBLISHED)
                .week(week)
                .questions(List.of(questions))
                .build();
    }

    // ------------------------------------------------------------------
    // Section A — calculateScore table (pure grading math)
    // ------------------------------------------------------------------

    static List<Object[]> scoreTable() {
        return List.of(
                // {answers, questions, expectedTotalScore, expectedNumCorrect, branch label}
                new Object[]{
                        "allCorrect",
                        List.of(answer(1L, "A"), answer(2L, "B")),
                        List.of(question(1L, AnswerChoices.A, 5), question(2L, AnswerChoices.B, 10)),
                        15.0, 2},
                new Object[]{
                        "mixedCorrectAndWrong",
                        List.of(answer(1L, "A"), answer(2L, "C")),
                        List.of(question(1L, AnswerChoices.A, 5), question(2L, AnswerChoices.B, 10)),
                        5.0, 1},
                new Object[]{
                        "allWrong_givesZero",
                        List.of(answer(1L, "D"), answer(2L, "C")),
                        List.of(question(1L, AnswerChoices.A, 5), question(2L, AnswerChoices.B, 10)),
                        0.0, 0},
                new Object[]{
                        "emptyAnswers_givesZero",
                        List.of(),
                        List.of(question(1L, AnswerChoices.A, 5)),
                        0.0, 0},
                new Object[]{
                        "unequalWeights_15_10_2",
                        List.of(answer(1L, "A"), answer(2L, "B"), answer(3L, "C")),
                        List.of(question(1L, AnswerChoices.A, 15), question(2L, AnswerChoices.B, 10),
                                question(3L, AnswerChoices.C, 2)),
                        27.0, 3}
        );
    }

    @ParameterizedTest(name = "grading table — {0}")
    @MethodSource("scoreTable")
    void calculateScore_tableDocumentsTodayBehavior(
            String branch, List<StudentAnswerDTO> answers, List<Question> questions,
            double expectedScore, int expectedCorrect) throws Exception {
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        QuizSubmissionServiceImpl.ScoreResult result = calculateScore(answers, questionMap);

        assertThat(result.getTotalScore()).isEqualTo(expectedScore);
        assertThat(result.getNumCorrect()).isEqualTo(expectedCorrect);
    }

    @Test
    @DisplayName("UNKNOWN question id is silently skipped (quizzes-workflow: submit path)")
    void unknownQuestionId_isSkipped_notCounted() throws Exception {
        // Current behavior (calculateScore :310-311): unknown questionId -> `continue`.
        // The extra answer neither adds points nor breaks anything.
        Map<Long, Question> questionMap = List.of(question(1L, AnswerChoices.A, 5)).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<StudentAnswerDTO> answers = List.of(answer(1L, "A"), answer(999L, "A"));

        QuizSubmissionServiceImpl.ScoreResult result = calculateScore(answers, questionMap);

        assertThat(result.getTotalScore()).isEqualTo(5.0);
        assertThat(result.getNumCorrect()).isEqualTo(1);
    }

    @Test
    @DisplayName("DUPLICATE answers for the same question DOUBLE-COUNT points (current behavior — flagged for Phase 4: GAME-03 family)")
    void duplicateAnswerSameQuestion_doubleCountsPoints() throws Exception {
        // Current behavior: no per-question dedupe; the same question answered twice
        // is counted twice (calculateScore :309-318 loops over the raw answer list).
        // Documented as it behaves today; P4-02/05 own any dedupe decision.
        Map<Long, Question> questionMap = List.of(question(1L, AnswerChoices.A, 5)).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<StudentAnswerDTO> answers = List.of(answer(1L, "A"), answer(1L, "A"));

        QuizSubmissionServiceImpl.ScoreResult result = calculateScore(answers, questionMap);

        assertThat(result.getTotalScore()).isEqualTo(10.0);
        assertThat(result.getNumCorrect()).isEqualTo(2);
    }

    @Test
    @DisplayName("Answer matching is CASE-SENSITIVE: 'a' != 'A' (current behavior)")
    void caseMismatch_isWrong() throws Exception {
        // Current behavior: String.equals (:314) — enum name "A" vs lowercase "a" fails.
        Map<Long, Question> questionMap = List.of(question(1L, AnswerChoices.A, 5)).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        QuizSubmissionServiceImpl.ScoreResult result =
                calculateScore(List.of(answer(1L, "a")), questionMap);

        assertThat(result.getTotalScore()).isEqualTo(0.0);
        assertThat(result.getNumCorrect()).isEqualTo(0);
    }

    @Test
    @DisplayName("Null selected answer -> NPE (current behavior; ties to SEL-07 family: missing @Valid on the submit path)")
    void nullSelectedAnswer_throwsNpe_today() {
        // Documented as it behaves today: no null-guard before .equals (:314).
        Map<Long, Question> questionMap = List.of(question(1L, AnswerChoices.A, 5)).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        // Arrays.asList (not List.of) — List.of rejects null elements.
        List<StudentAnswerDTO> answers = Arrays.asList(answer(1L, null));

        assertThatThrownBy(() -> calculateScore(answers, questionMap))
                .isInstanceOf(Exception.class) // InvocationTargetException wraps the NPE
                .hasCauseInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // Section B — late-penalty + points hand-off via the public submit path
    // (covers the inline math in submitQuizAnswers :106-145 — LEARN-09, LEARN-10)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("On-time submission: full score, delta applied once, no penalty (quizzes-workflow)")
    void onTimeSubmission_fullScore_noPenalty() {
        Quiz quiz = quizWith(
                question(1L, AnswerChoices.A, 5),
                question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email("rahma@test.com").build();
        // End date in the future -> not late.
        LocalDateTime now = LocalDateTime.now();
        QuizSubmission submission = QuizSubmission.builder()
                .id(9L).quiz(quiz).student(student).score(null)
                .startDate(now.minusMinutes(30))
                .endDate(now.plusMinutes(10))
                .answers(new ArrayList<>())
                .build();

        when(securityService.getCurrentUserEmail()).thenReturn("rahma@test.com");
        when(securityService.isStudentEnrolledByQuizId("rahma@test.com", 1L)).thenReturn(true);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(studentRepository.findByEmail("rahma@test.com")).thenReturn(Optional.of(student));
        when(quizSubmissionRepository.findByStudent_IdAndQuiz_Id(5L, 1L))
                .thenReturn(Optional.of(submission));

        QuizSubmissionDTO dto = QuizSubmissionDTO.builder()
                .answers(List.of(answer(1L, "A"), answer(2L, "B")))
                .build();

        QuizSubmissionResponseDTO resp = service.submitQuizAnswers(dto, 1L);

        // Full score: 5 + 10 = 15 (no penalty)
        assertThat(resp.getScore()).isEqualTo(15.0);
        assertThat(resp.getTotalPoints()).isEqualTo(15);
        assertThat(resp.getStatus()).isEqualTo("Submitted");
        assertThat(resp.getStudentName()).isEqualTo("Rahma Ismail");

        // Points delta = full score (LEARN-09 documents the (int) cast — value is integral today)
        verify(totalPointsService).applyDelta(student, quiz.getWeek().getMentorship(), 15);
        // Mentor + student both notified
        verify(notificationService, times(2))
                .sendToUserByEmail(anyString(), anyString(), anyString(), any(NotificationType.class));
    }

    @Test
    @DisplayName("Late submission: -1 point per minute late (LEARN-10 soft-close: -1pt/min penalty)")
    void lateSubmission_minusOnePerMinute() {
        Quiz quiz = quizWith(
                question(1L, AnswerChoices.A, 10),
                question(2L, AnswerChoices.B, 10));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email("rahma@test.com").build();
        // Ended 10.5 minutes ago -> 10 whole minutes late (ChronoUnit.MINUTES truncates).
        // +30s buffer keeps the minute count stable even if the test runs slowly.
        LocalDateTime now = LocalDateTime.now();
        QuizSubmission submission = QuizSubmission.builder()
                .id(9L).quiz(quiz).student(student).score(null)
                .startDate(now.minusMinutes(40))
                .endDate(now.minusSeconds(630)) // 10 min + 30 s in the past
                .answers(new ArrayList<>())
                .build();

        when(securityService.getCurrentUserEmail()).thenReturn("rahma@test.com");
        when(securityService.isStudentEnrolledByQuizId("rahma@test.com", 1L)).thenReturn(true);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(studentRepository.findByEmail("rahma@test.com")).thenReturn(Optional.of(student));
        when(quizSubmissionRepository.findByStudent_IdAndQuiz_Id(5L, 1L))
                .thenReturn(Optional.of(submission));

        QuizSubmissionDTO dto = QuizSubmissionDTO.builder()
                .answers(List.of(answer(1L, "A"), answer(2L, "B")))
                .build();

        QuizSubmissionResponseDTO resp = service.submitQuizAnswers(dto, 1L);

        // 20 raw - 10 late minutes = 10 (Math.max(0, 20-10) = 10, :115)
        assertThat(resp.getScore()).isEqualTo(10.0);
        verify(totalPointsService).applyDelta(student, quiz.getWeek().getMentorship(), 10);
    }

    @Test
    @DisplayName("Very late submission: penalty floors at 0, score never negative (:115 Math.max)")
    void veryLateSubmission_floorsAtZero() {
        Quiz quiz = quizWith(question(1L, AnswerChoices.A, 5));
        Student student = Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email("rahma@test.com").build();
        // Ended ~12.5 minutes ago; raw score only 5 -> penalty floors at 0.
        LocalDateTime now = LocalDateTime.now();
        QuizSubmission submission = QuizSubmission.builder()
                .id(9L).quiz(quiz).student(student).score(null)
                .startDate(now.minusMinutes(40))
                .endDate(now.minusSeconds(750)) // 12 min + 30 s in the past
                .answers(new ArrayList<>())
                .build();

        when(securityService.getCurrentUserEmail()).thenReturn("rahma@test.com");
        when(securityService.isStudentEnrolledByQuizId("rahma@test.com", 1L)).thenReturn(true);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(studentRepository.findByEmail("rahma@test.com")).thenReturn(Optional.of(student));
        when(quizSubmissionRepository.findByStudent_IdAndQuiz_Id(5L, 1L))
                .thenReturn(Optional.of(submission));

        QuizSubmissionDTO dto = QuizSubmissionDTO.builder()
                .answers(List.of(answer(1L, "A")))
                .build();

        QuizSubmissionResponseDTO resp = service.submitQuizAnswers(dto, 1L);

        // max(0, 5 - 12) = 0
        assertThat(resp.getScore()).isEqualTo(0.0);
        verify(totalPointsService).applyDelta(student, quiz.getWeek().getMentorship(), 0);
    }
}
