package com.example.gradproj.EduNest.service.completion;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** PostgreSQL-backed completion verification using an isolated disposable database. */
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CompletionLocalPostgresIntegrationTest extends ProdChainIntegrationTestBase {
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
    void uuidCompletionServiceProxyAndHttpEnforceMentorAuthorization() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-auth-owner-" + suffix + "@example.org";
        String otherMentor = "eb52-auth-other-" + suffix + "@example.org";
        String studentEmail = "eb52-auth-student-" + suffix + "@example.org";
        String adminEmail = "eb52-auth-admin-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        auth.mentor(otherMentor);
        auth.enroll(chain.mentorship().getId(), studentEmail);
        auth.admin(adminEmail);
        UUID publicId = chain.mentorship().getPublicId();
        long id = chain.mentorship().getId();
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(mentorship)).isTrue();

        try {
            for (String role : List.of("ROLE_STUDENT", "ROLE_ADMIN")) {
                // The owner's email isolates the role check from the separate ownership check.
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    owner, null, List.of(new SimpleGrantedAuthority(role))));
                assertThatThrownBy(() -> mentorship.updateMentorShipStatus(publicId, Status.COMPLETED))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isZero();
            }
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> mentorship.updateMentorShipStatus(publicId, Status.COMPLETED))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isZero();

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                otherMentor, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            assertThatThrownBy(() -> mentorship.updateMentorShipStatus(publicId, Status.COMPLETED))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isZero();
        } finally { SecurityContextHolder.clearContext(); }

        String route = "/api/v1/mentorship/update-status/{id}";
        mockMvc.perform(post(route, publicId).header("Authorization", auth.authToken(studentEmail, AuthFixture.STUDENT))
            .contentType("application/json").content("{\"status\":\"COMPLETED\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(route, publicId).header("Authorization", auth.authToken(adminEmail, AuthFixture.ADMIN))
            .contentType("application/json").content("{\"status\":\"COMPLETED\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(route, publicId).contentType("application/json")
            .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isZero();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(publicId, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForObject("SELECT status FROM mentorship WHERE id=?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id)).isEqualTo(1);
        worker.poll();
        worker.poll();
    }

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
        assertThat(status.status(chain.mentorship().getPublicId(), student)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isZero();
        jdbc.update("UPDATE total_points SET total_points=99 WHERE mentorship_id=?", id);
        worker.poll();
        assertThat(status.status(chain.mentorship().getPublicId(), student)).isEqualTo("READY");
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
        assertThatThrownBy(() -> mentorship.updateMentorShipStatus(chain.mentorship().getPublicId(), Status.COMPLETED)).isInstanceOf(RuntimeException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try {
            tx.executeWithoutResult(t -> { mentorship.updateMentorShipStatus(chain.mentorship().getPublicId(), Status.COMPLETED); t.setRollbackOnly(); });
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
            mockMvc.perform(get("/api/v1/student/certificates/mentorships/{id}/status", chain.mentorship().getPublicId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apiResponse.status").value("READY"));
            String outsider = "eb52-outsider-" + suffix + "@example.org";
            var outsiderStudent = auth.student(outsider);
            // An unrelated pre-existing certificate must not bypass a current job's frozen snapshot.
            jdbc.update("INSERT INTO certificates (student_id, mentorship_id, student_rank, issued_at, created_at, updated_at, created_by, updated_by, public_id) VALUES (?, ?, 2, now(), now(), now(), ?, ?, gen_random_uuid())", outsiderStudent.getId(), id, owner, owner);
            mockMvc.perform(get("/api/v1/student/certificates/mentorships/{id}/status", chain.mentorship().getPublicId())
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
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM mentorship_completion_snapshot s
            JOIN mentorship_completion_job j ON j.id=s.job_id
            LEFT JOIN certificates c ON c.mentorship_id=j.mentorship_id AND c.student_id=s.student_id
            WHERE j.mentorship_id=? AND (c.id IS NULL OR c.student_rank<>s.student_rank
              OR c.points_at_issuance<>s.points_at_completion OR c.cohort_size_at_issuance<>s.cohort_size)
            """, Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("""
            WITH ranked AS (
              SELECT student_id, row_number() OVER (ORDER BY total_points DESC, student_id ASC) AS expected_rank
              FROM total_points WHERE mentorship_id=?
            )
            SELECT count(*) FROM ranked r JOIN certificates c ON c.student_id=r.student_id AND c.mentorship_id=?
            WHERE c.student_rank<>r.expected_rank
            """, Long.class, id, id)).isZero();
        long jobId = jdbc.queryForObject("SELECT id FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id);
        UUID issuance = UUID.nameUUIDFromBytes(("edunest-completion-" + jobId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE certificate_issuance_id=?", Long.class, issuance)).isEqualTo(24002);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM (
              SELECT un.user_id, n.type, count(*) AS copies FROM user_notification un
              JOIN notification n ON n.id=un.notification_id WHERE un.certificate_issuance_id=?
              GROUP BY un.user_id,n.type HAVING count(*)<>1
            ) duplicates
            """, Long.class, issuance)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
    }

    @Test
    void databaseWriteExecutionsDoNotGrowWithCohortSize() {
        List<Long> writeExecutions = new java.util.ArrayList<>();
        for (int size : List.of(1, 25)) {
            String suffix = UUID.randomUUID().toString();
            String owner = "eb52-write-owner-" + suffix + "@example.org";
            String prefix = "eb52-write-student-" + suffix + "-";
            var chain = auth.mentorship(owner, Status.ACTIVE);
            long id = chain.mentorship().getId();
            Long role = jdbc.queryForObject("SELECT id FROM roles WHERE name='STUDENT'", Long.class);
            jdbc.update("""
                INSERT INTO users (deleted, enabled, created_at, updated_at, created_by, updated_by, email, first_name, role_id)
                SELECT false, true, now(), now(), ?, ?, ? || n || '@example.org', 'WriteCount', ? FROM generate_series(1,?) n
                """, owner, owner, prefix, role, size);
            jdbc.update("INSERT INTO students (student_id, educational_level) SELECT id, 'FRESH' FROM users WHERE email LIKE ?", prefix + "%");
            jdbc.update("""
                INSERT INTO enrollments (created_at, updated_at, created_by, updated_by, joined_at, mentorship_id, student_id)
                SELECT now(), now(), ?, ?, now(), ?, id FROM users WHERE email LIKE ?
                """, owner, owner, id, prefix + "%");
            jdbc.update("""
                INSERT INTO total_points (created_at, updated_at, created_by, updated_by, total_points, mentorship_id, student_id)
                SELECT now(), now(), ?, ?, 5, ?, id FROM users WHERE email LIKE ?
                """, owner, owner, id, prefix + "%");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
            try { mentorship.updateMentorShipStatus(chain.mentorship().getPublicId(), Status.COMPLETED); }
            finally { SecurityContextHolder.clearContext(); }
            JdbcTemplate tracked = org.mockito.Mockito.spy(new JdbcTemplate(dataSource));
            var localWorker = new CompletionWorker(tracked, org.mockito.Mockito.mock(SimpMessagingTemplate.class), transactionManager, 10, 300);
            localWorker.poll();
            localWorker.poll();
            long count = org.mockito.Mockito.mockingDetails(tracked).getInvocations().stream()
                .filter(invocation -> List.of("update", "query", "queryForObject").contains(invocation.getMethod().getName()))
                .filter(invocation -> invocation.getArguments().length > 0 && invocation.getArgument(0) instanceof String)
                .map(invocation -> ((String) invocation.getArgument(0)).toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("insert into certificates") || sql.contains("insert into notification")
                    || sql.contains("insert into user_notification") || sql.contains("insert into admin_notification"))
                .count();
            writeExecutions.add(count);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=?", Long.class, id)).isEqualTo(size);
            assertThat(jdbc.queryForObject("SELECT state FROM mentorship_completion_job WHERE mentorship_id=?", String.class, id)).isEqualTo("DONE");
        }
        // The spy includes nested JdbcTemplate overloads, so this is a conservative
        // upper bound on SQL-bearing invocations rather than a literal wire count.
        assertThat(writeExecutions.get(0)).isGreaterThanOrEqualTo(4).isLessThanOrEqualTo(20);
        assertThat(writeExecutions.get(1)).isEqualTo(writeExecutions.get(0));
    }

    @Test
    void bulkNotificationsRespectFrozenRankingAndEnrollmentRecipients() {
        String suffix = UUID.randomUUID().toString();
        String owner = "eb52-recipients-owner-" + suffix + "@example.org";
        String enrolledEmail = "eb52-enrolled-" + suffix + "@example.org";
        String rankedEmail = "eb52-ranked-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        long id = chain.mentorship().getId();
        var enrolled = auth.enroll(id, enrolledEmail).getStudent();
        var ranked = auth.student(rankedEmail);
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 7, now(), now(), ?, ?, gen_random_uuid(), 0)", ranked.getId(), id, owner, owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(chain.mentorship().getPublicId(), Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        worker.poll();
        worker.poll();
        assertThat(status.status(chain.mentorship().getPublicId(), enrolledEmail)).isEqualTo("NOT_ELIGIBLE");
        assertThat(status.status(chain.mentorship().getPublicId(), rankedEmail)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=? AND student_id=?", Long.class, id, ranked.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM certificates WHERE mentorship_id=? AND student_id=?", Long.class, id, enrolled.getId())).isZero();
        long jobId = jdbc.queryForObject("SELECT id FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id);
        UUID issuance = UUID.nameUUIDFromBytes(("edunest-completion-" + jobId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification un JOIN notification n ON n.id=un.notification_id WHERE un.certificate_issuance_id=? AND un.user_id=? AND n.type='MENTORSHIP'", Long.class, issuance, enrolled.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification un JOIN notification n ON n.id=un.notification_id WHERE un.certificate_issuance_id=? AND un.user_id=? AND n.type='CERTIFICATE'", Long.class, issuance, ranked.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE certificate_issuance_id=?", Long.class, issuance)).isEqualTo(2);
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
        assertThat(status.status(chain.mentorship().getPublicId(), email)).isEqualTo("FAILED");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        assertThatThrownBy(() -> reconciliation.retryExhausted(chain.mentorship().getPublicId(), "reviewed")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator@example.org", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try { reconciliation.retryExhausted(chain.mentorship().getPublicId(), "verified conflict resolved"); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForObject("SELECT reconciled_by FROM mentorship_completion_job WHERE id=?", String.class, job)).isEqualTo("operator@example.org");
        worker.poll();
        worker.poll();
        assertThat(status.status(chain.mentorship().getPublicId(), email)).isEqualTo("READY");
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
    void uuidEndpointsAndNotificationPayloadsUsePersistedPublicIds() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String owner = "uuid-owner-" + suffix + "@example.org";
        String studentEmail = "uuid-student-" + suffix + "@example.org";
        String adminEmail = "uuid-admin-" + suffix + "@example.org";
        var chain = auth.mentorship(owner, Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), studentEmail).getStudent();
        var admin = auth.admin(adminEmail);
        UUID publicId = chain.mentorship().getPublicId();
        long id = chain.mentorship().getId();
        jdbc.update("INSERT INTO total_points (student_id, mentorship_id, total_points, created_at, updated_at, created_by, updated_by, public_id, version) VALUES (?, ?, 1, now(), now(), ?, ?, gen_random_uuid(), 0)", student.getId(), id, owner, owner);
        String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);
        String adminToken = auth.authToken(adminEmail, AuthFixture.ADMIN);
        String route = "/api/v1/student/certificates/mentorships/{id}/status";
        mockMvc.perform(get(route, publicId).header("Authorization", adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(route, "not-a-uuid").header("Authorization", studentToken)).andExpect(status().isBadRequest());
        mockMvc.perform(get(route, UUID.randomUUID()).header("Authorization", studentToken)).andExpect(status().isNotFound());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorship.updateMentorShipStatus(publicId, Status.COMPLETED); }
        finally { SecurityContextHolder.clearContext(); }
        mockMvc.perform(get(route, publicId).header("Authorization", studentToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.apiResponse.status").value("PENDING"));
        String recovery = "/api/v1/admin/mentorships/{id}/completion-job/retry";
        mockMvc.perform(post(recovery, "not-a-uuid").header("Authorization", adminToken)
            .contentType("application/json").content("{\"reason\":\"reviewed\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(recovery, UUID.randomUUID()).header("Authorization", adminToken)
            .contentType("application/json").content("{\"reason\":\"reviewed\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(recovery, publicId).header("Authorization", studentToken)
            .contentType("application/json").content("{\"reason\":\"reviewed\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(recovery, publicId).header("Authorization", adminToken)
            .contentType("application/json").content("{\"reason\":\"reviewed\"}")).andExpect(status().isBadRequest());
        long jobId = jdbc.queryForObject("SELECT id FROM mentorship_completion_job WHERE mentorship_id=?", Long.class, id);
        jdbc.update("UPDATE mentorship_completion_job SET state='EXHAUSTED', attempt_count=5 WHERE id=?", jobId);
        mockMvc.perform(post(recovery, publicId).header("Authorization", adminToken)
            .contentType("application/json").content("{\"reason\":\"reviewed\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT reconciled_by FROM mentorship_completion_job WHERE id=?", String.class, jobId)).isEqualTo(adminEmail);
        var mockMessaging = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        var isolatedWorker = new CompletionWorker(jdbc, mockMessaging, transactionManager, 100, 300);
        isolatedWorker.poll();
        isolatedWorker.poll();
        UUID issuance = UUID.nameUUIDFromBytes(("edunest-completion-" + jobId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<UUID> studentIds = jdbc.queryForList("SELECT public_id FROM user_notification WHERE certificate_issuance_id=? AND user_id=?", UUID.class, issuance, student.getId());
        UUID adminId = jdbc.queryForObject("SELECT public_id FROM admin_notification WHERE admin_id=? AND content LIKE ?", UUID.class, admin.getId(), "%" + chain.mentorship().getTitle() + "%");
        var invocations = org.mockito.Mockito.mockingDetails(mockMessaging).getInvocations();
        List<UUID> pushedStudentIds = invocations.stream().filter(i -> i.getMethod().getName().equals("convertAndSendToUser") && studentEmail.equals(i.getArgument(0)))
            .map(i -> ((com.example.gradproj.EduNest.dto.notification.NotificationDto) i.getArgument(2)).getId()).toList();
        List<UUID> pushedAdminIds = invocations.stream().filter(i -> i.getMethod().getName().equals("convertAndSendToUser") && adminEmail.equals(i.getArgument(0)))
            .map(i -> ((com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse) i.getArgument(2)).getId()).toList();
        assertThat(pushedStudentIds).containsExactlyInAnyOrderElementsOf(studentIds).hasSize(2);
        assertThat(pushedAdminIds).containsExactly(adminId);
        for (UUID notificationId : studentIds) mockMvc.perform(get("/api/v1/notifications").header("Authorization", studentToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(notificationId.toString())));
        mockMvc.perform(get("/api/v1/admin/notifications").param("size", "100").header("Authorization", adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(adminId.toString())));
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
                        request.setStudentId(student.getPublicId()); started.countDown(); badgeAwards.awardBadge(badge.getId(), request);
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
                var answer = com.example.gradproj.EduNest.dto.quiz.request.StudentAnswerDTO.builder().questionId(question.getPublicId()).selectedAnswer("A").build();
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
