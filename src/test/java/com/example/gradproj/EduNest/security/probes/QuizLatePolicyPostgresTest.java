package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO;
import com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO;
import com.example.gradproj.EduNest.dto.quiz.response.QuizSubmissionResponseDTO;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.quiz.QuizCloseSweepService;
import com.example.gradproj.EduNest.service.quiz.question.QuestionServiceImp;
import com.example.gradproj.EduNest.service.quiz.quiz.QuizService;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import com.example.gradproj.EduNest.service.tasks.TaskSubmissionService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EB-53 / P4-04 (D-08) — late-submission & quiz close policy on PostgreSQL.
 *
 * Covers: task/project late-first acceptance within grace, rejection beyond
 * grace, late-resubmission rejection, quiz grace penalty math, beyond-grace
 * rejection with zero state mutation, closeAt enforcement for new attempts,
 * in-progress attempt preservation, scheduler close/idempotency/legacy-null
 * compatibility, mentor manual-close interaction, and post-close question
 * edit rejection.
 */
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class QuizLatePolicyPostgresTest extends ProdChainIntegrationTestBase {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.flyway.placeholders.defaultCommissionRate", () -> "10.0");
        r.add("app.scheduling.enabled", () -> false);
    }

    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired WeekRepository weeks;
    @Autowired QuizRepository quizzes;
    @Autowired QuizSubmissionRepository quizSubmissions;
    @Autowired QuestionRepository questions;
    @Autowired TaskRepository tasks;
    @Autowired TaskSubmissionRepository taskSubmissions;
    @Autowired ProjectRepository projects;
    @Autowired ProjectSubmissionRepository projectSubmissions;
    @Autowired QuizSubmissionService quizSubmissionService;
    @Autowired TaskSubmissionService taskSubmissionService;
    @Autowired com.example.gradproj.EduNest.service.projects.ProjectSubmissionService projectSubmissionService;
    @Autowired QuestionServiceImp questionService;
    @Autowired QuizService quizService;
    @Autowired QuizCloseSweepService quizCloseSweepService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /* ---------- fixtures ---------- */

    private void asStudent(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private void asMentor(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
    }

    private Week weekOf(AuthFixture.MentorshipChain chain) {
        return weeks.save(Week.builder().mentorship(chain.mentorship()).title("Week " + System.nanoTime()).build());
    }

    private Quiz publishedQuiz(Week week, LocalDateTime closeAt) {
        return quizzes.save(Quiz.builder()
                .week(week).title("Quiz " + System.nanoTime()).description("EB-53")
                .durationMinutes(30).status(QuizStatus.PUBLISHED).closeAt(closeAt).build());
    }

    private Question question(Quiz quiz, int points) {
        return questions.save(Question.builder()
                .quiz(quiz).text("Q " + System.nanoTime()).points(points)
                .correctAnswer(AnswerChoices.A)
                .optionA("A").optionB("B").optionC("C").optionD("D").build());
    }

    private QuizSubmission attempt(Quiz quiz, Student student, LocalDateTime endDate) {
        return quizSubmissions.save(QuizSubmission.builder()
                .quiz(quiz).student(student)
                .startDate(endDate.minusMinutes(30))
                .endDate(endDate)
                .build());
    }

    private QuizSubmissionDTO correctAnswers(Quiz quiz) {
        List<StudentAnswerDTO> answers = questions.findByQuiz_Id(quiz.getId()).stream()
                .map(q -> StudentAnswerDTO.builder().questionId(q.getPublicId()).selectedAnswer("A").build())
                .toList();
        return QuizSubmissionDTO.builder().answers(answers).build();
    }

    private Task publishedTask(Week week, LocalDateTime dueAt) {
        return tasks.save(Task.builder()
                .week(week).title("Task " + System.nanoTime()).description("EB-53")
                .points(10).passPoints(5).estimatedMinutes(30)
                .status(TaskStatus.PUBLISHED).dueAt(dueAt).build());
    }

    private Project publishedProject(Week week, LocalDateTime endAt) {
        return projects.save(Project.builder()
                .week(week).title("Project " + System.nanoTime()).brief("EB-53").goal("EB-53")
                .startAt(LocalDateTime.now().minusMinutes(5))
                .points(10).status(ProjectStatus.PUBLISHED).endAt(endAt).build());
    }

    private Integer totalPointsFor(Long mentorshipId, String studentEmail) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(tp.total_points), 0) FROM total_points tp " +
                "JOIN users u ON u.id = tp.student_id " +
                "WHERE tp.mentorship_id = ? AND u.email = ?", Integer.class, mentorshipId, studentEmail);
    }

    /* ---------- task & project policy ---------- */

    @Test
    void onTimeTaskAndProjectSubmissionsAcceptedAndNotFlaggedLate() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-ontime-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var task = publishedTask(week, LocalDateTime.now().plusMinutes(30));
        var project = publishedProject(week, LocalDateTime.now().plusMinutes(30));
        asStudent(student.getEmail());

        var taskResponse = taskSubmissionService.submit(task.getId(), null, "https://storage.example.org/eb53/on-time.pdf");
        assertThat(taskResponse.getIsLate()).isFalse();
        assertThat(taskResponse.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);

        var projectResponse = projectSubmissionService.submit(project.getId(), null, "https://storage.example.org/eb53/on-time.zip", student.getEmail());
        assertThat(projectResponse.getIsLate()).isFalse();
    }

    @Test
    void lateFirstTaskAndProjectSubmissionsInsideGraceAcceptedAndFlagged() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-grace-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var task = publishedTask(week, LocalDateTime.now().minusMinutes(3));
        var project = publishedProject(week, LocalDateTime.now().minusMinutes(3));
        asStudent(student.getEmail());

        var taskResponse = taskSubmissionService.submit(task.getId(), null, "https://storage.example.org/eb53/late-grace.pdf");
        assertThat(taskResponse.getIsLate()).isTrue();

        var projectResponse = projectSubmissionService.submit(project.getId(), null, "https://storage.example.org/eb53/late-grace.zip", student.getEmail());
        assertThat(projectResponse.getIsLate()).isTrue();
    }

    @Test
    void firstTaskAndProjectSubmissionsBeyondGraceRejectedWithoutStateMutation() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-beyond-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var task = publishedTask(week, LocalDateTime.now().minusMinutes(11));
        var project = publishedProject(week, LocalDateTime.now().minusMinutes(11));
        asStudent(student.getEmail());

        assertThatThrownBy(() -> taskSubmissionService.submit(task.getId(), null, "https://storage.example.org/eb53/beyond.pdf"))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("grace period");
        assertThatThrownBy(() -> projectSubmissionService.submit(project.getId(), null, "https://storage.example.org/eb53/beyond.zip", student.getEmail()))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("grace period");

        assertThat(taskSubmissions.findByTask_Id(task.getId(), PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(projectSubmissions.findAll()).noneMatch(s -> s.getProject().getId().equals(project.getId()));
    }

    @Test
    void lateResubmissionsStayRejectedForTasks() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-resub-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var task = publishedTask(week, LocalDateTime.now().plusMinutes(30));
        asStudent(student.getEmail());

        taskSubmissionService.submit(task.getId(), null, "https://storage.example.org/eb53/first.pdf");

        Task reloaded = tasks.findById(task.getId()).orElseThrow();
        reloaded.setDueAt(LocalDateTime.now().minusMinutes(2));
        tasks.save(reloaded);

        assertThatThrownBy(() -> taskSubmissionService.submit(task.getId(), null, "https://storage.example.org/eb53/second.pdf"))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("can't resubmit");
        var page = taskSubmissions.findByTask_Id(task.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getIsLate()).isFalse();
    }

    /* ---------- quiz policy ---------- */

    @Test
    void quizOnTimeSubmissionSavesFullScoreAndIsLateFalse() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-q-ontime-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        question(quiz, 2);
        attempt(quiz, student, LocalDateTime.now().plusMinutes(20));
        asStudent(student.getEmail());

        QuizSubmissionResponseDTO response = quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId());

        assertThat(response.getScore()).isEqualTo(4.0);
        assertThat(response.getIsLate()).isFalse();
        QuizSubmission row = quizSubmissions.findByStudent_IdAndQuiz_Id(student.getId(), quiz.getId()).orElseThrow();
        assertThat(row.getScore()).isEqualTo(4.0);
        assertThat(row.getIsLate()).isFalse();
        assertThat(totalPointsFor(chain.mentorship().getId(), student.getEmail())).isEqualTo(4);
    }

    @Test
    void quizLateSubmissionInsideGraceAppliesExactPenalty() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-q-late-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        question(quiz, 2);
        attempt(quiz, student, LocalDateTime.now().minusMinutes(3));
        asStudent(student.getEmail());

        QuizSubmissionResponseDTO response = quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId());

        assertThat(response.getScore()).isEqualTo(1.0);
        assertThat(response.getIsLate()).isTrue();
        QuizSubmission row = quizSubmissions.findByStudent_IdAndQuiz_Id(student.getId(), quiz.getId()).orElseThrow();
        assertThat(row.getScore()).isEqualTo(1.0);
        assertThat(row.getIsLate()).isTrue();
        assertThat(totalPointsFor(chain.mentorship().getId(), student.getEmail())).isEqualTo(1);
    }

    @Test
    void quizLatePenaltyFloorsAtZero() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-q-floor-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        attempt(quiz, student, LocalDateTime.now().minusMinutes(9));
        asStudent(student.getEmail());

        QuizSubmissionResponseDTO response = quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId());

        assertThat(response.getScore()).isEqualTo(0.0);
        assertThat(response.getIsLate()).isTrue();
        assertThat(totalPointsFor(chain.mentorship().getId(), student.getEmail())).isEqualTo(0);
    }

    @Test
    void quizSubmissionBeyondGraceRejectedWithoutAnyStateMutation() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-q-reject-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        question(quiz, 2);
        attempt(quiz, student, LocalDateTime.now().minusMinutes(11));
        asStudent(student.getEmail());

        assertThatThrownBy(() -> quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId()))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("window has closed");

        QuizSubmission row = quizSubmissions.findByStudent_IdAndQuiz_Id(student.getId(), quiz.getId()).orElseThrow();
        assertThat(row.getScore()).isNull();
        assertThat(row.getIsLate()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_answer WHERE submission_id = ?",
                Integer.class, row.getId())).isZero();
        assertThat(totalPointsFor(chain.mentorship().getId(), student.getEmail())).isEqualTo(0);
    }

    @Test
    void newAttemptsProhibitedAfterCloseAtWithoutTouchingExistingAttempts() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var fresh = auth.enroll(chain.mentorship().getId(), "eb53-q-fresh-" + suffix + "@example.org").getStudent();
        var continuing = auth.enroll(chain.mentorship().getId(), "eb53-q-cont-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().minusMinutes(5));
        question(quiz, 2);
        question(quiz, 2);
        attempt(quiz, continuing, LocalDateTime.now().plusMinutes(25));
        asStudent(fresh.getEmail());

        assertThatThrownBy(() -> questionService.getQuestionsByQuizIdForStudent(quiz.getId()))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("closed for new attempts");
        assertThat(quizSubmissions.existsByStudent_IdAndQuiz_Id(fresh.getId(), quiz.getId())).isFalse();

        asStudent(continuing.getEmail());
        assertThat(questionService.getQuestionsByQuizIdForStudent(quiz.getId())).hasSize(2);
        QuizSubmissionResponseDTO response = quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId());
        assertThat(response.getScore()).isEqualTo(4.0);
        assertThat(response.getIsLate()).isFalse();
    }

    /* ---------- scheduler / lifecycle ---------- */

    @Test
    void schedulerClosesQuizWithZeroAttemptsAfterCloseAtAndIsIdempotent() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), "eb53-sched-zero-" + suffix + "@example.org");
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().minusMinutes(15));
        question(quiz, 2);

        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isEqualTo(1);
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.CLOSED);
        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isZero();
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.CLOSED);

        // closed quizzes no longer serve students
        var student = auth.enroll(chain.mentorship().getId(), "eb53-sched-zero-student-" + suffix + "@example.org").getStudent();
        asStudent(student.getEmail());
        assertThatThrownBy(() -> questionService.getQuestionsByQuizIdForStudent(quiz.getId()))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("not published");
    }

    @Test
    void schedulerWaitsForInFlightAttemptButClosesAfterItsEligibleWindowExpires() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-sched-inflight-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().minusMinutes(15));
        question(quiz, 2);
        var attemptRow = attempt(quiz, student, LocalDateTime.now().plusMinutes(5));

        // closeAt passed, but the attempt is still inside its duration window
        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isZero();
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.PUBLISHED);

        // inside grace: still not closed
        jdbc.update("UPDATE quiz_submission SET end_date = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(3), attemptRow.getId());
        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isZero();
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.PUBLISHED);

        // grace expired: the last eligible attempt is gone -> close
        jdbc.update("UPDATE quiz_submission SET end_date = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(11), attemptRow.getId());
        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isEqualTo(1);
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.CLOSED);
    }

    @Test
    void legacyQuizWithNullCloseAtIsNeverAutoClosed() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), "eb53-legacy-" + suffix + "@example.org");
        var week = weekOf(chain);
        var legacyQuiz = quizzes.save(Quiz.builder()
                .week(week).title("Legacy " + System.nanoTime()).description("EB-53")
                .durationMinutes(30).status(QuizStatus.PUBLISHED).closeAt(null).build());

        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isZero();
        assertThat(quizzes.findById(legacyQuiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.PUBLISHED);

        // legacy quizzes still accept new attempts (pre-EB-53 behavior preserved)
        var student = auth.enroll(chain.mentorship().getId(), "eb53-legacy-student-" + suffix + "@example.org").getStudent();
        asStudent(student.getEmail());
        assertThat(questionService.getQuestionsByQuizIdForStudent(legacyQuiz.getId())).isEmpty();
        assertThat(quizSubmissions.existsByStudent_IdAndQuiz_Id(student.getId(), legacyQuiz.getId())).isTrue();
    }

    @Test
    void mentorManualCloseStillWorksAlongsideAutoClose() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        asMentor(chain.mentor().getEmail());

        quizService.changeStatus(quiz.getId(), QuizStatus.CLOSED);
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.CLOSED);

        // sweep must not resurrect or double-process closed quizzes
        assertThat(quizCloseSweepService.autoCloseExpiredQuizzes(LocalDateTime.now())).isZero();
    }

    @Test
    void publishingRequiresAFutureCloseAt() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var week = weekOf(chain);
        var quiz = quizzes.save(Quiz.builder()
                .week(week).title("Publish guard " + System.nanoTime()).description("EB-53")
                .durationMinutes(30).status(QuizStatus.DRAFT).build());
        question(quiz, 2);
        asMentor(chain.mentor().getEmail());

        assertThatThrownBy(() -> quizService.changeStatus(quiz.getId(), QuizStatus.PUBLISHED))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("closeAt is required");

        Quiz reloaded = quizzes.findById(quiz.getId()).orElseThrow();
        reloaded.setCloseAt(LocalDateTime.now().plusHours(1));
        quizzes.save(reloaded);
        quizService.changeStatus(quiz.getId(), QuizStatus.PUBLISHED);
        assertThat(quizzes.findById(quiz.getId()).orElseThrow().getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void questionEditsStayRejectedAfterClosure() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        Question q = question(quiz, 2);
        asMentor(chain.mentor().getEmail());

        assertThatThrownBy(() -> questionService.updateQuestion(
                q.getId(),
                com.example.gradproj.EduNest.dto.quiz.request.QuestionUpdateDto.builder()
                        .quizId(quiz.getPublicId()).text("edited").points(3).build()))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("already published or closed");
    }

    /* ---------- EB-51 integrity regression pin ---------- */

    @Test
    void doubleSubmitRemainsRejectedAfterLatePolicyChange() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb53-owner-" + suffix + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb53-double-" + suffix + "@example.org").getStudent();
        var week = weekOf(chain);
        var quiz = publishedQuiz(week, LocalDateTime.now().plusHours(2));
        question(quiz, 2);
        attempt(quiz, student, LocalDateTime.now().plusMinutes(20));
        asStudent(student.getEmail());

        quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId());
        assertThatThrownBy(() -> quizSubmissionService.submitQuizAnswers(correctAnswers(quiz), quiz.getId()))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("already submitted");
    }
}
