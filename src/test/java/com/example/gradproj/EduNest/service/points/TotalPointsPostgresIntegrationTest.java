package com.example.gradproj.EduNest.service.points;
import com.example.gradproj.EduNest.service.livesession.LiveSessionSweepWorker;
import com.example.gradproj.EduNest.service.livesession.LiveSessionServiceImp;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.service.week.WeekService;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.service.livesession.policy.PointsAwardingPolicy;
import com.example.gradproj.EduNest.service.quiz.quiz.QuizService;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import java.time.LocalDateTime;
import com.example.gradproj.EduNest.service.quiz.question.QuestionService;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.dto.quiz.request.QuestionCreateDTO;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.entity.quiz.Question;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.dto.badges.request.UpdateBadgeRequest;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.service.badges.BadgeService;
import jakarta.persistence.EntityManager;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class TotalPointsPostgresIntegrationTest extends ProdChainIntegrationTestBase {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
    @Autowired private TotalPointsRepository points;
    @Autowired private TotalPointsServiceImp service;
    @Autowired private TransactionTemplate transactions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private BadgeRepository badges;
    @Autowired private BadgeService badgeService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private WeekService weekService;
    @Autowired private WeekRepository weekRepository;
    @Autowired private MentorShipRepository mentorShipRepository;
    @Autowired private QuizService quizService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizSubmissionRepository quizSubmissions;
    @Autowired private LiveSessionRepository sessions;
    @Autowired private AttendanceRepository attendance;
    @Autowired private QuestionService questionService;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private LiveSessionSweepWorker sweepWorker;
    @Autowired private LiveSessionServiceImp liveSessionService;
    @Autowired private SessionAttendanceResultRepository sessionAttendanceResults;

    @Test
    void versionedNewBadgeAndLedgerRowsPersistOnFlywaySchema() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb51-version-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("eb51-version-student-" + suffix + "@example.org");
        var badge = Badge.builder()
                .mentorship(chain.mentorship()).title("Version test").description("version test")
                .category(BadgeCategory.ACHIEVEMENT).points(10).build();
        var ledger = TotalPoints.builder().student(student).mentorship(chain.mentorship()).totalPoints(23).build();
        assertThat(badge.getVersion()).isNull();
        assertThat(ledger.getVersion()).isNull();
        var savedBadge = badges.saveAndFlush(badge);
        var savedLedger = points.saveAndFlush(ledger);
        assertThat(savedBadge.getId()).isNotNull();
        assertThat(savedLedger.getId()).isNotNull();
        assertThat(savedBadge.getVersion()).isZero();
        assertThat(savedLedger.getVersion()).isZero();
        assertThat(points.findById(savedLedger.getId()).orElseThrow().getTotalPoints()).isEqualTo(23);
    }

    @Test
    void concurrentFirstInsertsAccumulateAndRollbackDoesNotPersist() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-points-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-points-student-" + suffix + "@example.org");
        int workers = 6;
        var pool = Executors.newFixedThreadPool(workers);
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < workers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    service.applyDelta(student, chain.mentorship(), 5);
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var result : results) result.get(45, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                .orElseThrow().getTotalPoints()).isEqualTo(workers * 5);

        transactions.executeWithoutResult(status -> {
            service.applyDelta(student, chain.mentorship(), -10);
            status.setRollbackOnly();
        });
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                .orElseThrow().getTotalPoints()).isEqualTo(workers * 5);
    }

    @Test
    void concurrentMixedDeltasOnExistingRowDoNotLoseUpdates() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-mixed-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-mixed-student-" + suffix + "@example.org");
        points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship())
                .totalPoints(100).build());
        int workers = 6;
        var pool = Executors.newFixedThreadPool(workers);
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < workers; i++) {
                final int delta = i % 2 == 0 ? 5 : -5;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    service.applyDelta(student, chain.mentorship(), delta);
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var result : results) result.get(45, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                .orElseThrow().getTotalPoints()).isEqualTo(100);
    }

    @Test
    void badgeEditSeesCompletionCommittedAfterEntityWasLoaded() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-badge-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("badge").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        var pool = Executors.newSingleThreadExecutor();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentorEmail, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            transactions.executeWithoutResult(status -> {
                Badge loaded = badges.findById(badge.getId()).orElseThrow();
                assertThat(loaded.getMentorship().getStatus()).isEqualTo(Status.ACTIVE);
                try {
                    pool.submit(() -> jdbc.update("update mentorship set status = 'COMPLETED' where id = ?",
                            chain.mentorship().getId())).get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                UpdateBadgeRequest request = new UpdateBadgeRequest();
                request.setPoints(20);
                assertThatThrownBy(() -> badgeService.updateBadge(badge.getId(), request))
                        .isInstanceOf(globalLogicEx.class)
                        .hasMessageContaining("completed");
                status.setRollbackOnly();
            });
        } finally {
            SecurityContextHolder.clearContext();
            pool.shutdownNow();
        }
        assertThat(badges.findById(badge.getId()).orElseThrow().getPoints()).isEqualTo(10);
    }

    @Test
    void badgeEditPreservesCallerChangesAlreadyManagedByOuterTransaction() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-outer-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("original").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentorEmail, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            transactions.executeWithoutResult(status -> {
                Badge managed = entityManager.find(Badge.class, badge.getId());
                managed.setTitle("outer pending change");
                UpdateBadgeRequest request = new UpdateBadgeRequest();
                request.setDescription("changed description");
                badgeService.updateBadge(badge.getId(), request);
                assertThat(managed.getTitle()).isEqualTo("outer pending change");
                status.setRollbackOnly();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void atomicDeltaSurvivesLaterFlushOfPreviouslyManagedLedgerRow() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-flush-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-flush-student-" + suffix + "@example.org");
        points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship())
                .totalPoints(10).build());

        transactions.executeWithoutResult(status -> {
            TotalPoints managed = points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                    .orElseThrow();
            service.applyDelta(student, chain.mentorship(), 5);
            managed.setUpdatedBy("outer-caller@example.org");
            entityManager.flush();
        });

        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                .orElseThrow().getTotalPoints()).isEqualTo(15);
    }

    @Test
    void atomicDecrementSurvivesLaterFlushOfPreviouslyManagedLedgerRow() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-decrement-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-decrement-student-" + suffix + "@example.org");
        points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship())
                .totalPoints(10).build());
        transactions.executeWithoutResult(status -> {
            TotalPoints managed = points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                    .orElseThrow();
            service.applyDelta(student, chain.mentorship(), -4);
            managed.setUpdatedBy("outer-caller@example.org");
            entityManager.flush();
        });
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                .orElseThrow().getTotalPoints()).isEqualTo(6);
    }

    @Test
    void firstUpsertCanBeReadAsManagedEntityInSameTransaction() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-first-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-first-student-" + suffix + "@example.org");
        transactions.executeWithoutResult(status -> {
            service.applyDelta(student, chain.mentorship(), 7);
            TotalPoints managed = points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())
                    .orElseThrow();
            assertThat(entityManager.contains(managed)).isTrue();
            assertThat(managed.getTotalPoints()).isEqualTo(7);
            status.setRollbackOnly();
        });
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())).isEmpty();
    }

    @Test
    void diagnosticMentorshipRefreshCascadesAndReplacesPendingBadgeChanges() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-cascade-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("original").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        transactions.executeWithoutResult(status -> {
            Badge managed = entityManager.find(Badge.class, badge.getId());
            var mentorship = managed.getMentorship();
            assertThat(mentorship.getBadges()).extracting(Badge::getId).contains(badge.getId());
            managed.setTitle("pending title");
            entityManager.refresh(mentorship);
            // Diagnostic assertion of the entity mapping's current cascade behavior.
            assertThat(managed.getTitle()).isEqualTo("original");
            status.setRollbackOnly();
        });
    }

    @Test
    void badgeTitleConflictIsRejectedRatherThanOverwritingConcurrentCommit() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-conflict-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("original").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        var pool = Executors.newSingleThreadExecutor();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentorEmail, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            transactions.executeWithoutResult(status -> {
                assertThat(entityManager.find(Badge.class, badge.getId()).getTitle()).isEqualTo("original");
                try {
                    pool.submit(() -> jdbc.update("update badges set title = ? where id = ?", "other transaction", badge.getId()))
                            .get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                UpdateBadgeRequest request = new UpdateBadgeRequest();
                request.setTitle("caller request");
                assertThatThrownBy(() -> badgeService.updateBadge(badge.getId(), request))
                        .isInstanceOf(globalLogicEx.class);
                status.setRollbackOnly();
            });
        } finally {
            SecurityContextHolder.clearContext();
            pool.shutdownNow();
        }
        assertThat(badges.findById(badge.getId()).orElseThrow().getTitle()).isEqualTo("other transaction");
    }

    @Test
    void badgeEditDoesNotDiscardUnrelatedDirtyStudent() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-unrelated-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("pg-unrelated-student-" + suffix + "@example.org");
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("badge").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentorEmail, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            transactions.executeWithoutResult(status -> {
                var managed = entityManager.find(com.example.gradproj.EduNest.entity.users.Student.class, student.getId());
                managed.setFirstName("pending name");
                UpdateBadgeRequest request = new UpdateBadgeRequest();
                request.setDescription("new description");
                badgeService.updateBadge(badge.getId(), request);
                assertThat(managed.getFirstName()).isEqualTo("pending name");
                status.setRollbackOnly();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void repeatedAtomicDeltasAccumulateWithinOneTransactionWithoutManagedLedger() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-repeat-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-repeat-student-" + suffix + "@example.org");
        transactions.executeWithoutResult(status -> {
            service.applyDelta(student, chain.mentorship(), 10);
            service.applyDelta(student, chain.mentorship(), 5);
            service.applyDelta(student, chain.mentorship(), -3);
            entityManager.flush();
            Integer value = jdbc.queryForObject("select total_points from total_points where student_id = ? and mentorship_id = ?",
                    Integer.class, student.getId(), chain.mentorship().getId());
            assertThat(value).isEqualTo(12);
            status.setRollbackOnly();
        });
        assertThat(points.findByStudent_IdAndMentorship_Id(student.getId(), chain.mentorship().getId())).isEmpty();
    }

    @Test
    void hibernateLoadedSnapshotRetainsOriginalBadgeTitleAfterCallerEdit() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-snapshot-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("original").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        transactions.executeWithoutResult(status -> {
            Badge managed = entityManager.find(Badge.class, badge.getId());
            managed.setTitle("caller title");
            var session = entityManager.unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            var entry = session.getPersistenceContextInternal().getEntry(managed);
            assertThat(entry).isNotNull();
            assertThat(entry.getLoadedState()).isNotNull();
            assertThat(entry.getLoadedValue("title")).isEqualTo("original");
            assertThat(managed.getTitle()).isEqualTo("caller title");
            status.setRollbackOnly();
        });
    }

    @Test
    void readOnlyManagedBadgeHasNoLoadedSnapshotForConflictComparison() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-readonly-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Badge badge = badges.saveAndFlush(Badge.builder().mentorship(chain.mentorship())
                .title("original").description("description").category(BadgeCategory.ACHIEVEMENT).points(10).build());
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status -> {
            Badge managed = entityManager.find(Badge.class, badge.getId());
            var session = entityManager.unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            var entry = session.getPersistenceContextInternal().getEntry(managed);
            assertThat(entry).isNotNull();
            assertThat(entry.isReadOnly()).isTrue();
            assertThat(entry.getLoadedState()).isNull();
        });
    }
    @Test
    void weekCascadeDeleteRejectsDeletionWhenLiveSessionIsLiveOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-live-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());
        sessions.saveAndFlush(Session.builder()
                .week(week)
                .title("Live Zoom Call")
                .status(SessionStatus.LIVE)
                .scheduledAt(LocalDateTime.now().minusHours(1))
                .actualStartTime(LocalDateTime.now().minusMinutes(30))
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThatThrownBy(() -> weekService.deleteWeek(week.getId()))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot delete week that contains submissions or session attendance records");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void weekCascadeDeleteRejectsDeletionWhenAttendanceExistsOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-att-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-att-student-" + suffix + "@example.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 2").build());
        Session session = sessions.saveAndFlush(Session.builder()
                .week(week)
                .title("Ended Zoom Call")
                .status(SessionStatus.ENDED)
                .scheduledAt(LocalDateTime.now().minusDays(1))
                .build());
        attendance.saveAndFlush(SessionAttendance.builder()
                .session(session)
                .student(student)
                .snapshotTime(LocalDateTime.now().minusDays(1))
                                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThatThrownBy(() -> weekService.deleteWeek(week.getId()))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot delete week that contains submissions or session attendance records");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void quizChangeStatusRejectsTransitionOfClosedQuizOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-quiz-close-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 3").build());
        Quiz quiz = quizRepository.saveAndFlush(Quiz.builder()
                .week(week)
                .title("Final Closed Quiz")
                .description("Final closed quiz description")
                .durationMinutes(30)
                .status(QuizStatus.CLOSED)
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThatThrownBy(() -> quizService.changeStatus(quiz.getId(), QuizStatus.PUBLISHED))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot change status of a closed quiz");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void quizChangeStatusRejectsRevertingScoredQuizToDraftOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-quiz-draft-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("pg-quiz-draft-student-" + suffix + "@example.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 4").build());
        Quiz quiz = quizRepository.saveAndFlush(Quiz.builder()
                .week(week)
                .title("Active Quiz")
                .description("Active quiz description")
                .durationMinutes(30)
                .status(QuizStatus.PUBLISHED)
                .build());
        quizSubmissions.saveAndFlush(QuizSubmission.builder()
                .quiz(quiz)
                .student(student)
                .score(85.0)
                .startDate(LocalDateTime.now().minusMinutes(20))
                .endDate(LocalDateTime.now())
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            assertThatThrownBy(() -> quizService.changeStatus(quiz.getId(), QuizStatus.DRAFT))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot revert quiz with submissions to draft");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void questionCreationRejectsWhenQuizIsPublishedOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-q-pub-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());
        Quiz quiz = quizRepository.saveAndFlush(Quiz.builder()
                .week(week)
                .title("Published Quiz")
                .description("Desc")
                .durationMinutes(30)
                .status(QuizStatus.PUBLISHED)
                .build());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            QuestionCreateDTO dto = QuestionCreateDTO.builder()
                    .quizId(quiz.getId())
                    .text("Question text")
                    .points(2)
                    .correctAnswer(AnswerChoices.A)
                    .optionA("A").optionB("B").optionC("C").optionD("D")
                    .build();

            assertThatThrownBy(() -> questionService.createQuestion(dto))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot add question. Quiz is already published or closed.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void questionDeleteRejectsWhenQuizIsClosedOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("pg-q-del-mentor-" + suffix + "@example.org", Status.ACTIVE);
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Week 1").build());
        Quiz quiz = quizRepository.saveAndFlush(Quiz.builder()
                .week(week)
                .title("Closed Quiz")
                .description("Desc")
                .durationMinutes(30)
                .status(QuizStatus.CLOSED)
                .build());
        Question question = Question.builder()
                .quiz(quiz)
                .text("Question to delete")
                .points(2)
                .correctAnswer(AnswerChoices.A)
                .optionA("A").optionB("B").optionC("C").optionD("D")
                .build();
        question = questionRepository.saveAndFlush(question);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                chain.mentor().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            Long questionId = question.getId();
            assertThatThrownBy(() -> questionService.deleteQuestion(quiz.getId(), questionId))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Cannot delete question. Quiz is already published or closed.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void liveSessionSweepAndManualEndSessionSerializeSafelyOnPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-sweep-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("pg-sweep-student-" + suffix + "@example.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Sweep Week").build());
        Session session = sessions.saveAndFlush(Session.builder()
                .week(week)
                .title("Concurrent Zoom Call")
                .status(SessionStatus.LIVE)
                .scheduledAt(LocalDateTime.now().minusHours(2))
                .actualStartTime(LocalDateTime.now().minusHours(2))
                .build());

        attendance.saveAndFlush(SessionAttendance.builder()
                .session(session)
                .student(student)
                .snapshotTime(LocalDateTime.now().minusHours(1))
                .build());

        int threads = 2;
        var pool = Executors.newFixedThreadPool(threads);
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);

        var futureSweep = pool.submit(() -> {
            ready.countDown();
            if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
            return sweepWorker.closeExpiredSession(session.getId(), LocalDateTime.now());
        });

        var futureManual = pool.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    mentorEmail, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            try {
                ready.countDown();
                if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                liveSessionService.endSession(session.getId());
                return true;
            } catch (Exception ex) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        futureSweep.get(45, TimeUnit.SECONDS);
        futureManual.get(45, TimeUnit.SECONDS);
        pool.shutdownNow();

        Session refreshed = sessions.findById(session.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(SessionStatus.ENDED);

        List<SessionAttendanceResult> results = sessionAttendanceResults.findBySession_Id(session.getId());
        assertThat(results.size()).isLessThanOrEqualTo(1);
    }

    @Test
    void twoConcurrentSweepWorkersSerializeAndAwardPointsExactlyOnceOnPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-sweep2-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("pg-sweep2-student-" + suffix + "@example.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Sweep Week 2").build());
        Session session = sessions.saveAndFlush(Session.builder()
                .week(week)
                .title("Concurrent Workers Session")
                .status(SessionStatus.LIVE)
                .scheduledAt(LocalDateTime.now().minusHours(2))
                .actualStartTime(LocalDateTime.now().minusHours(2))
                .build());

        attendance.saveAndFlush(SessionAttendance.builder()
                .session(session)
                .student(student)
                .snapshotTime(LocalDateTime.now().minusHours(1))
                .build());

        int threads = 2;
        var pool = Executors.newFixedThreadPool(threads);
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);

        var future1 = pool.submit(() -> {
            ready.countDown();
            if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
            return sweepWorker.closeExpiredSession(session.getId(), LocalDateTime.now());
        });

        var future2 = pool.submit(() -> {
            ready.countDown();
            if (!start.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
            return sweepWorker.closeExpiredSession(session.getId(), LocalDateTime.now());
        });

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        boolean result1 = future1.get(45, TimeUnit.SECONDS);
        boolean result2 = future2.get(45, TimeUnit.SECONDS);
        pool.shutdownNow();

        // Exactly one worker wins and transitions the session, the other exits gracefully
        assertThat(result1 ^ result2).isTrue();

        Session refreshed = sessions.findById(session.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(SessionStatus.ENDED);

        // Verification of session attendance results and constraint integrity
        List<SessionAttendanceResult> results = sessionAttendanceResults.findBySession_Id(session.getId());
        assertThat(results.size()).isLessThanOrEqualTo(1);
    }

    @Test
    void attendanceInsertionAndPointsMutationRollbackTogetherOnFailureOnPostgres() {
        String suffix = UUID.randomUUID().toString();
        String mentorEmail = "pg-rollback-mentor-" + suffix + "@example.org";
        var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        var student = auth.student("pg-rollback-student-" + suffix + "@example.org");
        Week week = weekRepository.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("Rollback Week").build());
        Session session = sessions.saveAndFlush(Session.builder()
                .week(week)
                .title("Failing Session")
                .status(SessionStatus.LIVE)
                .scheduledAt(LocalDateTime.now().minusHours(2))
                .actualStartTime(LocalDateTime.now().minusHours(2))
                .build());

        attendance.saveAndFlush(SessionAttendance.builder()
                .session(session)
                .student(student)
                .snapshotTime(LocalDateTime.now().minusHours(1))
                .build());

        // Construct worker with failing PointsAwardingPolicy that throws RuntimeException
        PointsAwardingPolicy failingPolicy = (s, res) -> {
            sessionAttendanceResults.save(SessionAttendanceResult.builder()
                    .session(s)
                    .student(student)
                    .attended(true)
                    .build());
            throw new RuntimeException("Simulated points ledger failure");
        };

        LiveSessionSweepWorker workerWithFailure = new LiveSessionSweepWorker(
                sessions,
                mentorShipRepository,
                weekRepository,
                attendance,
                (s, snaps) -> java.util.List.of(),
                failingPolicy,
                null,
                null,
                null
        );

        // Execute worker under transaction template to verify transactional rollback
        assertThatThrownBy(() -> transactions.execute(status -> workerWithFailure.closeExpiredSession(session.getId(), LocalDateTime.now())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated points ledger failure");

        // Verify that everything rolled back:
        // 1. Session is still LIVE in PostgreSQL database!
        Session afterRollback = sessions.findById(session.getId()).orElseThrow();
        assertThat(afterRollback.getStatus()).isEqualTo(SessionStatus.LIVE);

        // 2. The partial SessionAttendanceResult was rolled back and does NOT exist in PostgreSQL!
        List<SessionAttendanceResult> resultsAfterRollback = sessionAttendanceResults.findBySession_Id(session.getId());
        assertThat(resultsAfterRollback).isEmpty();

        // 3. Retry with healthy policy succeeds!
        PointsAwardingPolicy healthyPolicy = (s, res) -> {
            sessionAttendanceResults.save(SessionAttendanceResult.builder()
                    .session(s)
                    .student(student)
                    .attended(true)
                    .build());
        };
        LiveSessionSweepWorker healthyWorker = new LiveSessionSweepWorker(
                sessions,
                mentorShipRepository,
                weekRepository,
                attendance,
                (s, snaps) -> java.util.List.of(),
                healthyPolicy,
                null,
                null,
                null
        );

        boolean retryResult = Boolean.TRUE.equals(transactions.execute(status ->
                healthyWorker.closeExpiredSession(session.getId(), LocalDateTime.now())
        ));
        assertThat(retryResult).isTrue();

        Session afterRetry = sessions.findById(session.getId()).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(SessionStatus.ENDED);
        List<SessionAttendanceResult> resultsAfterRetry = sessionAttendanceResults.findBySession_Id(session.getId());
        assertThat(resultsAfterRetry).hasSize(1);
    }
}
