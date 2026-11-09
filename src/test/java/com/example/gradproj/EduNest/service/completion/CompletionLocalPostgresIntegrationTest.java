package com.example.gradproj.EduNest.service.completion;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import javax.sql.DataSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Opt-in only: URL must point at an isolated, disposable V1-V11 PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "EB52_DISPOSABLE_PG_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55439/eb52review")
class CompletionLocalPostgresIntegrationTest extends ProdChainIntegrationTestBase {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("EB52_DISPOSABLE_PG_URL"));
        r.add("spring.datasource.username", () -> "eb52test");
        r.add("spring.datasource.password", () -> "");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> false);
        r.add("app.scheduling.enabled", () -> false);
        r.add("app.completion.worker.enabled", () -> true);
    }

    @Autowired AuthFixture auth;
    @Autowired mentorShipService mentorship;
    @Autowired CompletionWorker worker;
    @Autowired CompletionStatusService status;
    @Autowired CompletionReconciliationService reconciliation;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired com.example.gradproj.EduNest.repository.badges.BadgeRepository badges;
    @Autowired com.example.gradproj.EduNest.service.badges.BadgeAwardService badgeAwards;
    @Autowired com.example.gradproj.EduNest.repository.week.WeekRepository weeks;
    @Autowired com.example.gradproj.EduNest.repository.tasks.TaskRepository tasks;
    @Autowired com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository taskSubmissions;
    @Autowired com.example.gradproj.EduNest.repository.projects.ProjectRepository projects;
    @Autowired com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository projectSubmissions;
    @Autowired com.example.gradproj.EduNest.repository.quiz.QuizRepository quizzes;
    @Autowired com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository quizSubmissions;
    @Autowired com.example.gradproj.EduNest.repository.quiz.QuestionRepository questions;
    @Autowired com.example.gradproj.EduNest.service.tasks.TaskSubmissionService taskGrading;
    @Autowired com.example.gradproj.EduNest.service.projects.ProjectSubmissionService projectGrading;
    @Autowired com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService quizGrading;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void completionCommitsSnapshotThenWorkerIssuesOnce() {
        String suffix = UUID.randomUUID().toString();
        String mentor = "eb52-mentor-" + suffix + "@example.org";
        String student = "eb52-student-" + suffix + "@example.org";
        var chain = auth.mentorship(mentor, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var enrolled = auth.enroll(id, student).getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 7, now(), now(), ?, ?, gen_random_uuid(), 0)", enrolled.getId(), id, mentor, mentor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentor, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            mentorship.updateMentorShipStatus(id, Status.COMPLETED);
            mentorship.updateMentorShipStatus(id, Status.COMPLETED);
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(status.status(id, student)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isZero();
        jdbc.update("UPDATE total_points SET total_points=99 WHERE mentorship_id=?", id);
        worker.poll();
        assertThat(status.status(id, student)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT points_at_issuance FROM certificates WHERE mentorship_id=?", Integer.class, id)).isEqualTo(7);
        worker.poll();
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void rollbackRemovesSnapshotAndUnauthorizedOwnerCannotComplete() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-own-" + suffix + "@example.org";
        String foreign = "eb52-other-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        auth.mentor(foreign);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, "eb52-roll-" + suffix + "@example.org").getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                foreign, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        assertThatThrownBy(() -> mentorship.updateMentorShipStatus(id, Status.COMPLETED)).isInstanceOf(RuntimeException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            tx.executeWithoutResult(t -> { mentorship.updateMentorShipStatus(id, Status.COMPLETED); t.setRollbackOnly(); });
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isZero();
    }

    @Test
    void failedCertificateCohortRollsBackAndCanRetry() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-fail-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var first = auth.enroll(id, "eb52-f1-" + suffix + "@example.org").getStudent();
        var second = auth.enroll(id, "eb52-f2-" + suffix + "@example.org").getStudent();
        for (var s : List.of(first, second)) jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", s.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        jdbc.update("INSERT INTO certificates (student_id, mentorship_id, student_rank, issued_at, created_at, updated_at, created_by, updated_by, public_id) VALUES (?, ?, 2, now(), now(), now(), ?, ?, gen_random_uuid())", second.getId(), id, owner, owner);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("RETRY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=? AND student_id=?", Long.class, id, first.getId())).isZero();
        jdbc.update("DELETE FROM certificates WHERE mentorship_id=? AND student_id=?", id, second.getId());
        jdbc.update("UPDATE mentorship_completion_job SET next_attempt_at=clock_timestamp() WHERE mentorship_id=?", id);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(2);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
    }

    @Test
    void concurrentCompletionsAndWorkersDoNotDuplicateDurableEffects() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-race-" + suffix + "@example.org";
        String studentEmail = "eb52-rs-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, studentEmail).getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
                try { start.await(); mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
                finally { SecurityContextHolder.clearContext(); }
                return true;
            })).toList();
            start.countDown();
            for (var call : calls) assertThat(call.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
            var one = pool.submit(worker::poll);
            var two = pool.submit(worker::poll);
            one.get(30, TimeUnit.SECONDS);
            two.get(30, TimeUnit.SECONDS);
            worker.poll();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification un JOIN notification n ON n.id=un.notification_id WHERE un.user_id=? AND n.title='Certificate Issued!'", Long.class, student.getId())).isEqualTo(1);
            String token = auth.authToken(studentEmail, AuthFixture.STUDENT);
            mockMvc.perform(get("/api/v1/student/certificates/mentorships/{id}/status", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apiResponse.status").value("READY"));
            String outsider = "eb52-outsider-" + suffix + "@example.org";
            var outsiderStudent = auth.student(outsider);
            // An unrelated pre-existing certificate must not bypass a current job's frozen snapshot.
            jdbc.update("INSERT INTO certificates (student_id, mentorship_id, student_rank, issued_at, created_at, updated_at, created_by, updated_by, public_id) VALUES (?, ?, 2, now(), now(), now(), ?, ?, gen_random_uuid())", outsiderStudent.getId(), id, owner, owner);
            mockMvc.perform(get("/api/v1/student/certificates/mentorships/{id}/status", id)
                .header("Authorization", auth.authToken(outsider, AuthFixture.STUDENT))).andExpect(status().isNotFound());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void twelveThousandStudentsCompleteWithCohortAtomicity() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-scale-" + suffix + "@example.org";
        String prefix = "eb52-s-" + suffix + "-";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        Long role = jdbc.queryForObject("SELECT id FROM roles WHERE name='STUDENT'", Long.class);
        jdbc.update("""
            INSERT INTO users (deleted, enabled, created_at, updated_at, created_by, updated_by, email, first_name, role_id)
            SELECT false, true, now(), now(), ?, ?, ? || n || '@example.org', 'Scale', ? FROM generate_series(1,12001) n
            """, owner, owner, prefix, role);
        jdbc.update("INSERT INTO students (student_id, educational_level) SELECT id, 'FRESH' FROM users WHERE email LIKE ?", prefix + "%");
        jdbc.update("""
            INSERT INTO enrollments (created_at, updated_at, created_by, updated_by, joined_at, mentorship_id, student_id)
            SELECT now(), now(), ?, ?, now(), ?, id FROM users WHERE email LIKE ?
            """, owner, owner, id, prefix + "%");
        jdbc.update("""
            INSERT INTO total_points (created_at, updated_at, created_by, updated_by, total_points, mentorship_id, student_id)
            SELECT now(), now(), ?, ?, (id % 5)::integer, ?, id FROM users WHERE email LIKE ?
            """, owner, owner, id, prefix + "%");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        long started = System.nanoTime();
        try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        long snapshotMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        started = System.nanoTime();
        worker.poll();
        long certificatesMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        started = System.nanoTime();
        worker.poll();
        long notificationsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        System.out.printf("EB52 cohort=12001 snapshot_ms=%d certificates_ms=%d notifications_ms=%d%n", snapshotMs, certificatesMs, notificationsMs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(12001);
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
    }

    @Test
    void exhaustedJobCanOnlyBeReconciledByAdmin() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-reconcile-" + suffix + "@example.org";
        String email = "eb52-reconcile-student-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, email).getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        long job = jdbc.queryForObject("SELECT id FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id);
        jdbc.update("UPDATE mentorship_completion_job SET state='EXHAUSTED', attempt_count=5 WHERE id=?", job);
        assertThat(status.status(id, email)).isEqualTo("FAILED");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        assertThatThrownBy(() -> reconciliation.retryExhausted(job, "reviewed")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator@example.org", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try { reconciliation.retryExhausted(job, "verified conflict resolved"); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForObject("SELECT reconciled_by FROM mentorship_completion_job WHERE id=?", String.class, job)).isEqualTo("operator@example.org");
        worker.poll();
        worker.poll();
        assertThat(status.status(id, email)).isEqualTo("READY");
    }

    @Test
    void enrollmentWaitingForCompletionLockCannotJoinCompletedMentorship() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-lock-owner-" + suffix + "@example.org";
        String newcomer = "eb52-lock-new-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        auth.enroll(id, "eb52-lock-existing-" + suffix + "@example.org");
        auth.student(newcomer);
        var pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        try {
            final java.util.concurrent.Future<?>[] waiting = new java.util.concurrent.Future<?>[1];
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            tx.executeWithoutResult(t -> {
                jdbc.queryForObject("SELECT id FROM mentorship WHERE id=? FOR UPDATE", Long.class, id);
                waiting[0] = pool.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(newcomer, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
                    try { started.countDown(); mentorship.joinMentorship(id); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                mentorship.updateMentorShipStatus(id, Status.COMPLETED);
            });
            assertThatThrownBy(() -> waiting[0].get(30, TimeUnit.SECONDS))
                .hasCauseInstanceOf(com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollments e JOIN users u ON u.id=e.student_id WHERE e.mentorship_id=? AND u.email=?", Long.class, id, newcomer)).isZero();
            worker.poll();
            worker.poll();
        } finally { SecurityContextHolder.clearContext(); pool.shutdownNow(); }
    }

    @Test
    void snapshotExcludesUncommittedPointsAndStaleClaimRecovers() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-snapshot-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var first = auth.enroll(id, "eb52-sa-" + suffix + "@example.org").getStudent();
        var second = auth.enroll(id, "eb52-sb-" + suffix + "@example.org").getStudent();
        for (var s : List.of(first, second)) jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 5, now(), now(), ?, ?, gen_random_uuid(), 0)", s.getId(), id, owner, owner);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE total_points SET total_points=99 WHERE student_id=? AND mentorship_id=?")) {
                statement.setLong(1, first.getId()); statement.setLong(2, id); statement.executeUpdate();
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
            finally { SecurityContextHolder.clearContext(); }
            connection.commit();
        }
        long job = jdbc.queryForObject("SELECT id FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id);
        assertThat(jdbc.queryForObject("SELECT points_at_completion FROM mentorship_completion_snapshot WHERE job_id=? AND student_id=?", Integer.class, job, first.getId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT student_rank FROM mentorship_completion_snapshot WHERE job_id=? AND student_id=?", Long.class, job, first.getId())).isLessThan(jdbc.queryForObject("SELECT student_rank FROM mentorship_completion_snapshot WHERE job_id=? AND student_id=?", Long.class, job, second.getId()));
        UUID oldToken = UUID.randomUUID();
        jdbc.update("UPDATE mentorship_completion_job SET state='RUNNING', claim_token=?, claim_generation=7, lease_until=clock_timestamp() - interval '1 second', attempt_count=1 WHERE id=?", oldToken, job);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT claim_generation FROM mentorship_completion_job WHERE id=?", Long.class, job)).isEqualTo(8);
        List<CompletionWorker.AdminPush> stale = tx.execute(t -> worker.process(new CompletionWorker.Claim(job, oldToken, 7)));
        assertThat(stale).isNull();
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE id=?", String.class, job)).isEqualTo("DONE");
    }

    @Test
    void notificationFailureRollsBackRecordsAndRetryDoesNotDuplicateCertificates() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-notify-" + suffix + "@example.org";
        String email = "eb52-notify-student-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, email).getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        worker.poll();
        jdbc.execute("ALTER TABLE notification ADD CONSTRAINT eb52_test_reject_certificate CHECK (title <> 'Certificate Issued!') NOT VALID");
        try {
            worker.poll();
            assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("RETRY");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification un JOIN notification n ON n.id=un.notification_id WHERE un.user_id=? AND n.title='Certificate Issued!'", Long.class, student.getId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE notification DROP CONSTRAINT eb52_test_reject_certificate"); }
        jdbc.update("UPDATE mentorship_completion_job SET next_attempt_at=clock_timestamp() WHERE mentorship_id=?", id);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification un JOIN notification n ON n.id=un.notification_id WHERE un.user_id=? AND n.title='Certificate Issued!'", Long.class, student.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void pendingAndCertificateCommittedJobsResumeWithNewWorkerInstance() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-restart-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, "eb52-restart-s-" + suffix + "@example.org").getStudent();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        new CompletionWorker(jdbc, messaging, transactionManager, 100, 300).poll();
        assertThat(jdbc.queryForObject("SELECT stage FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("NOTIFICATIONS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
        // Simulates a new process starting after the certificate-stage transaction has committed.
        new CompletionWorker(jdbc, messaging, transactionManager, 100, 300).poll();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void enrollmentCommittedBeforeCompletionAppearsInSnapshot() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-joinfirst-" + suffix + "@example.org";
        String email = "eb52-joinfirst-s-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        auth.student(email);
        var joined = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var join = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
                try { tx.executeWithoutResult(t -> { mentorship.joinMentorship(id); joined.countDown(); try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); } catch (InterruptedException e) { throw new RuntimeException(e); } }); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(joined.await(20, TimeUnit.SECONDS)).isTrue();
            var complete = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
                try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
                finally { SecurityContextHolder.clearContext(); }
            });
            release.countDown();
            join.get(30, TimeUnit.SECONDS); complete.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_snapshot s JOIN mentorship_completion_job j ON j.id=s.job_id JOIN users u ON u.id=s.student_id WHERE j.mentorship_id=? AND u.email=? AND s.was_enrolled", Long.class, id, email)).isEqualTo(1);
            worker.poll(); worker.poll();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void badgeAwardWaitingForCompletionLockIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-badge-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var student = auth.enroll(id, "eb52-badge-s-" + suffix + "@example.org").getStudent();
        var badge = badges.saveAndFlush(com.example.gradproj.EduNest.entity.badges.Badge.builder()
            .mentorship(chain.mentorship()).title("EB52 " + suffix).description("test")
            .category(com.example.gradproj.EduNest.enums.badge.BadgeCategory.ACHIEVEMENT).points(5).build());
        var executor = Executors.newSingleThreadExecutor();
        try {
            final java.util.concurrent.Future<?>[] waiting = new java.util.concurrent.Future<?>[1];
            var started = new CountDownLatch(1);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            tx.executeWithoutResult(t -> {
                jdbc.queryForObject("SELECT id FROM mentorship WHERE id=? FOR UPDATE", Long.class, id);
                waiting[0] = executor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
                    try {
                        var request = new com.example.gradproj.EduNest.dto.badges.request.AwardBadgeRequest();
                        request.setStudentId(student.getId()); started.countDown(); badgeAwards.awardBadge(badge.getId(), request);
                    } finally { SecurityContextHolder.clearContext(); }
                });
                try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                mentorship.updateMentorShipStatus(id, Status.COMPLETED);
            });
            assertThatThrownBy(() -> waiting[0].get(30, TimeUnit.SECONDS))
                .hasCauseInstanceOf(com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM badge_awards WHERE badge_id=? AND student_id=?", Long.class, badge.getId(), student.getId())).isZero();
            worker.poll(); worker.poll();
        } finally { SecurityContextHolder.clearContext(); executor.shutdownNow(); }
    }

    @Test
    void actualTaskProjectAndQuizWritesRespectSnapshotCutoff() throws Exception {
        for (String kind : List.of("task", "project", "quiz")) {
            String suffix = UUID.randomUUID().toString();
            String owner = "eb52-" + kind + "-owner-" + suffix + "@example.org";
            String studentEmail = "eb52-" + kind + "-student-" + suffix + "@example.org";
            var chain = auth.mentorship(owner, Status.ACTIVE);
            long id = chain.mentorship().getId();
            var student = auth.enroll(id, studentEmail).getStudent();
            jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 5, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
            var week = weeks.saveAndFlush(com.example.gradproj.EduNest.entity.mentorship.Week.builder().mentorship(chain.mentorship()).title("Week").build());
            Runnable grade;
            if (kind.equals("task")) {
                var task = tasks.saveAndFlush(com.example.gradproj.EduNest.entity.tasks.Task.builder().week(week).title("Task").description("Test")
                    .points(100).passPoints(50).estimatedMinutes(60).status(com.example.gradproj.EduNest.enums.tasks.TaskStatus.PUBLISHED).dueAt(java.time.LocalDateTime.now().plusDays(2)).build());
                var sub = taskSubmissions.saveAndFlush(com.example.gradproj.EduNest.entity.tasks.TaskSubmission.builder().task(task).student(student)
                    .status(com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.SUBMITTED).fileUrl("https://example.org/x").submittedAt(java.time.LocalDateTime.now()).pointsApplied(5).isLate(false).build());
                grade = () -> { var request = new com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest(); request.setScore(15); request.setFeedback("test"); taskGrading.grade(sub.getId(), request); };
            } else if (kind.equals("project")) {
                var project = projects.saveAndFlush(com.example.gradproj.EduNest.entity.projects.Project.builder().week(week).title("Project").brief("Test").goal("Test")
                    .points(100).startAt(java.time.LocalDateTime.now().minusDays(1)).endAt(java.time.LocalDateTime.now().plusDays(2)).status(com.example.gradproj.EduNest.enums.project.ProjectStatus.PUBLISHED).build());
                var sub = projectSubmissions.saveAndFlush(com.example.gradproj.EduNest.entity.projects.ProjectSubmission.builder().project(project).student(student)
                    .status(com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.SUBMITTED).fileUrl("https://example.org/x").submittedAt(java.time.LocalDateTime.now()).pointsApplied(5).isLate(false).build());
                grade = () -> { var request = new com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest(); request.setScore(15); request.setFeedback("test"); projectGrading.gradeProject(sub.getId(), request, owner); };
            } else {
                var quiz = quizzes.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.Quiz.builder().week(week).title("Quiz").description("Test").durationMinutes(60).status(com.example.gradproj.EduNest.enums.quiz.QuizStatus.PUBLISHED).build());
                var question = questions.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.Question.builder().quiz(quiz).text("Q").points(10).correctAnswer(com.example.gradproj.EduNest.enums.quiz.AnswerChoices.A).optionA("a").optionB("b").optionC("c").optionD("d").build());
                quizSubmissions.saveAndFlush(com.example.gradproj.EduNest.entity.quiz.QuizSubmission.builder().quiz(quiz).student(student).startDate(java.time.LocalDateTime.now()).endDate(java.time.LocalDateTime.now().plusHours(1)).answers(new java.util.ArrayList<>()).build());
                var answer = com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO.builder().questionId(question.getId()).selectedAnswer("A").build();
                var request = com.example.gradproj.EduNest.dto.quiz.request.QuizSubmissionDTO.builder().answers(List.of(answer)).build();
                grade = () -> quizGrading.submitQuizAnswers(request, quiz.getId());
            }
            var graded = new CountDownLatch(1); var finish = new CountDownLatch(1);
            var pool = Executors.newSingleThreadExecutor();
            try {
                var future = pool.submit(() -> {
                    String actor = kind.equals("quiz") ? studentEmail : owner;
                    String role = kind.equals("quiz") ? "ROLE_STUDENT" : "ROLE_MENTOR";
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(role))));
                    try { tx.executeWithoutResult(t -> { grade.run(); entityManager.flush(); graded.countDown(); try { if (!finish.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); } catch (InterruptedException e) { throw new RuntimeException(e); } }); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                assertThat(graded.await(20, TimeUnit.SECONDS)).as(kind + " grade flush").isTrue();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
                try { mentorship.updateMentorShipStatus(id, Status.COMPLETED); }
                finally { SecurityContextHolder.clearContext(); finish.countDown(); }
                future.get(30, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT points_at_completion FROM mentorship_completion_snapshot s JOIN mentorship_completion_job j ON j.id=s.job_id WHERE j.mentorship_id=? AND s.student_id=?", Integer.class, id, student.getId())).as(kind).isEqualTo(5);
                assertThat(jdbc.queryForObject("SELECT total_points FROM total_points WHERE mentorship_id=? AND student_id=?", Integer.class, id, student.getId())).as(kind).isEqualTo(15);
                worker.poll(); worker.poll();
                assertThat(jdbc.queryForObject("SELECT points_at_issuance FROM certificates WHERE mentorship_id=? AND student_id=?", Integer.class, id, student.getId())).as(kind).isEqualTo(5);
            } finally { finish.countDown(); pool.shutdownNow(); }
        }
    }
}
