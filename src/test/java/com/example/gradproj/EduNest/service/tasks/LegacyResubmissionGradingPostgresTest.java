package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.LegacySubmissionAuditRequiredException;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionServiceImpl;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EB-51 Blocker 1 & Final Integrity Verification:
 * PostgreSQL Integration Tests for Legacy & Sprint 2 Submission Grading Paths.
 *
 * Verifies:
 * 1. Genuine new submission with pointsApplied = 0 grades successfully.
 * 2. Existing submission with pointsApplied = 0 grades successfully.
 * 3. Existing submission with known non-null credit regrades using correct delta.
 * 4. Historical graded-then-resubmitted record with pointsApplied = null is rejected and balance remains unchanged.
 * 5. Historical genuinely ungraded record with pointsApplied = null is rejected pending administrative audit.
 * 6. Concurrent grading does not double-credit (ordered pessimistic locking).
 * 7. Failed grading fully rolls back.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("EB-51: PostgreSQL Submission Grading Integrity & Legacy Safety Tests")
public class LegacyResubmissionGradingPostgresTest extends ProdChainIntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weekRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskSubmissionRepository taskSubmissionRepository;
    @Autowired private TaskSubmissionServiceImpl taskSubmissionService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectSubmissionRepository projectSubmissionRepository;
    @Autowired private ProjectSubmissionServiceImpl projectSubmissionService;
    @Autowired private TotalPointsRepository totalPointsRepository;

    private void authenticateMentor(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Task: Provenance-backed regrading credits no duplicate points using delta math")
    void taskLegacyResubmissionReproductionAndIntegrityProtection() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-task-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-task-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("Legacy Task 1")
                .description("Task description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        // Step 1: 80 points credited historically
        totalPointsRepository.saveAndFlush(TotalPoints.builder()
                .student(student)
                .mentorship(chain.mentorship())
                .totalPoints(80)
                .version(0L)
                .build());

        // Step 2: Submission has pointsApplied = 80 established via administrative reconciliation
        TaskSubmission legacyResubmission = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .fileUrl("https://example.org/resubmitted.zip")
                .submittedAt(LocalDateTime.now().minusHours(1))
                .pointsApplied(80)
                .isLate(false)
                .build());

        authenticateMentor(mentorEmail);
        try {
            // Mentor regrades submission with score 95
            GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
            req.setScore(95);
            req.setFeedback("Improved work on resubmission");

            taskSubmissionService.grade(legacyResubmission.getId(), req);

            // Delta: 95 - 80 = +15. TotalPoints: 80 + 15 = 95. NOT 80 + 95 = 175!
            TotalPoints tp = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tp.getTotalPoints()).isEqualTo(95);

            TaskSubmission graded = taskSubmissionRepository.findById(legacyResubmission.getId()).orElseThrow();
            assertThat(graded.getPointsApplied()).isEqualTo(95);
            assertThat(graded.getFinalScore()).isEqualTo(95);
            assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Project: Provenance-backed regrading credits no duplicate points using delta math")
    void projectLegacyResubmissionReproductionAndIntegrityProtection() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-proj-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-proj-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Project project = projectRepository.saveAndFlush(Project.builder()
                .week(week)
                .title("Legacy Project 1")
                .brief("Brief description")
                .goal("Learning goal")
                .points(100)
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().plusDays(5))
                .status(ProjectStatus.PUBLISHED)
                .build());

        // 70 points credited historically
        totalPointsRepository.saveAndFlush(TotalPoints.builder()
                .student(student)
                .mentorship(chain.mentorship())
                .totalPoints(70)
                .version(0L)
                .build());

        ProjectSubmission legacyResubmission = projectSubmissionRepository.saveAndFlush(ProjectSubmission.builder()
                .project(project)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .fileUrl("https://example.org/project-resubmitted.zip")
                .submittedAt(LocalDateTime.now().minusHours(1))
                .pointsApplied(70)
                .isLate(false)
                .build());

        authenticateMentor(mentorEmail);
        try {
            GradeProjectSubmissionRequest req = new GradeProjectSubmissionRequest();
            req.setScore(85);
            req.setFeedback("Well done on project revisions");

            projectSubmissionService.gradeProject(legacyResubmission.getId(), req, mentorEmail);

            // Delta: 85 - 70 = +15. TotalPoints: 70 + 15 = 85. NOT 70 + 85 = 155!
            TotalPoints tp = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tp.getTotalPoints()).isEqualTo(85);

            ProjectSubmission graded = projectSubmissionRepository.findById(legacyResubmission.getId()).orElseThrow();
            assertThat(graded.getPointsApplied()).isEqualTo(85);
            assertThat(graded.getFinalScore()).isEqualTo(85);
            assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Sprint 2 resubmissions retain pointsApplied across resubmit, guaranteeing zero duplicate credit")
    void sprint2ResubmissionRetainsPointsAppliedAndPreventsDoubleCredit() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-sprint2-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-sprint2-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("Sprint 2 Task")
                .description("Desc")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        TaskSubmission sub = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .fileUrl("https://example.org/first.zip")
                .submittedAt(LocalDateTime.now().minusDays(1))
                .pointsApplied(0)
                .isLate(false)
                .build());

        authenticateMentor(mentorEmail);
        try {
            // First grading in Sprint 2: 75 points
            GradeTaskSubmissionRequest req1 = new GradeTaskSubmissionRequest();
            req1.setScore(75);
            req1.setFeedback("Initial grade");
            taskSubmissionService.grade(sub.getId(), req1);

            TotalPoints tp1 = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tp1.getTotalPoints()).isEqualTo(75);

            // Student resubmits: rawScore/finalScore/gradedAt cleared, but pointsApplied preserved as 75!
            TaskSubmission loaded = taskSubmissionRepository.findById(sub.getId()).orElseThrow();
            loaded.setStatus(SubmissionStatus.SUBMITTED);
            loaded.setRawScore(null);
            loaded.setFinalScore(null);
            loaded.setGradedAt(null);
            taskSubmissionRepository.saveAndFlush(loaded);

            // Mentor regrades the resubmission with 90 points
            GradeTaskSubmissionRequest req2 = new GradeTaskSubmissionRequest();
            req2.setScore(90);
            req2.setFeedback("Resubmission reviewed");
            taskSubmissionService.grade(sub.getId(), req2);

            // Delta is 90 - 75 = 15; total points become 75 + 15 = 90!
            TotalPoints tp2 = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tp2.getTotalPoints()).isEqualTo(90);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Strict Integrity Gate: Historical null pointsApplied records are rejected and balances remain unchanged")
    void strictIntegrityGate_historicalNullRecordsRejectedAndBalancesUnchanged() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-gate-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var studentA = auth.student("student-a-" + suffix + "@test.org");
        var studentB = auth.student("student-b-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("Gate Task")
                .description("Gate Task description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        // Record A: Historical genuinely ungraded, initial balance 0, pointsApplied == null
        totalPointsRepository.saveAndFlush(TotalPoints.builder()
                .student(studentA).mentorship(chain.mentorship()).totalPoints(0).version(0L).build());
        TaskSubmission recordA = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task).student(studentA).status(SubmissionStatus.SUBMITTED)
                .pointsApplied(null).fileUrl("https://example.org/a.zip").build());

        // Record B: Historical graded-then-resubmitted, initial balance 80, pointsApplied == null
        totalPointsRepository.saveAndFlush(TotalPoints.builder()
                .student(studentB).mentorship(chain.mentorship()).totalPoints(80).version(0L).build());
        TaskSubmission recordB = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task).student(studentB).status(SubmissionStatus.SUBMITTED)
                .pointsApplied(null).fileUrl("https://example.org/b.zip").build());

        authenticateMentor(mentorEmail);
        try {
            GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
            req.setScore(95);
            req.setFeedback("Grading attempt");

            // Record A is rejected pending administrative audit
            assertThatThrownBy(() -> taskSubmissionService.grade(recordA.getId(), req))
                    .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                    .hasMessageContaining("lacks recorded points provenance");

            // Record B is rejected to prevent double credit
            assertThatThrownBy(() -> taskSubmissionService.grade(recordB.getId(), req))
                    .isInstanceOf(LegacySubmissionAuditRequiredException.class)
                    .hasMessageContaining("lacks recorded points provenance");

            // Assert: BOTH student balances remain strictly unchanged!
            TotalPoints tpA = totalPointsRepository.findByStudent_IdAndMentorship_Id(studentA.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tpA.getTotalPoints()).isEqualTo(0);

            TotalPoints tpB = totalPointsRepository.findByStudent_IdAndMentorship_Id(studentB.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tpB.getTotalPoints()).isEqualTo(80);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Genuine new submission (pointsApplied initialized to 0) grades successfully")
    void genuineNewSubmission_withInitialPointsAppliedZero_gradesSuccessfully() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-new-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-new-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("New Submission Task")
                .description("New Task description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        // Student creates submission in Sprint 2 -> pointsApplied defaults to 0 explicitly
        TaskSubmission sub = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(0)
                .fileUrl("https://example.org/new.zip")
                .build());

        authenticateMentor(mentorEmail);
        try {
            GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
            req.setScore(85);
            req.setFeedback("Excellent work");

            taskSubmissionService.grade(sub.getId(), req);

            TotalPoints tp = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();
            assertThat(tp.getTotalPoints()).isEqualTo(85);

            TaskSubmission graded = taskSubmissionRepository.findById(sub.getId()).orElseThrow();
            assertThat(graded.getPointsApplied()).isEqualTo(85);
            assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Failed grading fully rolls back: invalid score leaves submission and points intact")
    void failedGrading_fullyRollsBackOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-rollback-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-rollback-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("Rollback Task")
                .description("Rollback Task description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        TaskSubmission sub = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(0)
                .fileUrl("https://example.org/rollback.zip")
                .build());

        authenticateMentor(mentorEmail);
        try {
            // Attempt to grade with 150 > task.getPoints() (100)
            GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
            req.setScore(150);
            req.setFeedback("Over max points");

            assertThatThrownBy(() -> taskSubmissionService.grade(sub.getId(), req))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("score must be less than or equal to task points");

            // Assert: Submission remains SUBMITTED, pointsApplied remains 0, TotalPoints unchanged
            TaskSubmission loaded = taskSubmissionRepository.findById(sub.getId()).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(loaded.getPointsApplied()).isEqualTo(0);
            assertThat(loaded.getFinalScore()).isNull();

            var tpOpt = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId());
            assertThat(tpOpt).isEmpty();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Concurrent grading does not double-credit on PostgreSQL due to ordered pessimistic locking")
    void concurrentGradingDoesNotDoubleCreditOnPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "mentor-concurr-" + suffix + "@test.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("student-concurr-" + suffix + "@test.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());

        Task task = taskRepository.saveAndFlush(Task.builder()
                .week(week)
                .title("Concurrent Task")
                .description("Concurrent Task description")
                .points(100)
                .passPoints(50)
                .estimatedMinutes(60)
                .status(TaskStatus.PUBLISHED)
                .dueAt(LocalDateTime.now().plusDays(5))
                .build());

        TaskSubmission sub = taskSubmissionRepository.saveAndFlush(TaskSubmission.builder()
                .task(task)
                .student(student)
                .status(SubmissionStatus.SUBMITTED)
                .pointsApplied(0)
                .fileUrl("https://example.org/concurr.zip")
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable gradeAction1 = () -> {
            authenticateMentor(mentorEmail);
            try {
                startLatch.await();
                GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
                req.setScore(80);
                req.setFeedback("Concurrent grade 1");
                taskSubmissionService.grade(sub.getId(), req);
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                SecurityContextHolder.clearContext();
                doneLatch.countDown();
            }
        };

        Runnable gradeAction2 = () -> {
            authenticateMentor(mentorEmail);
            try {
                startLatch.await();
                GradeTaskSubmissionRequest req = new GradeTaskSubmissionRequest();
                req.setScore(90);
                req.setFeedback("Concurrent grade 2");
                taskSubmissionService.grade(sub.getId(), req);
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                SecurityContextHolder.clearContext();
                doneLatch.countDown();
            }
        };

        executor.submit(gradeAction1);
        executor.submit(gradeAction2);
        startLatch.countDown();

        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Both or at least one completed; TotalPoints must match the final pointsApplied in the submission row!
        TaskSubmission graded = taskSubmissionRepository.findById(sub.getId()).orElseThrow();
        TotalPoints tp = totalPointsRepository.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId()).orElseThrow();

        assertThat(tp.getTotalPoints()).isEqualTo(graded.getPointsApplied());
        assertThat(tp.getTotalPoints()).isIn(80, 90); // Exactly either 80 or 90, NEVER 80 + 90 = 170!
    }
}