package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.certificate.CertificateRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.service.certificate.CertificateService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real Spring issuance with Flyway-managed disposable PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CertificateIssuancePostgresIntegrationTest extends ProdChainIntegrationTestBase {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.placeholders.defaultCommissionRate", () -> "10.0");
        registry.add("app.certificate.issuance-batch-size", () -> 2);
    }

    @Autowired private AuthFixture auth;
    @Autowired private TotalPointsRepository points;
    @Autowired private CertificateService service;
    @Autowired private CertificateRepository certificates;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @SpyBean private SimpMessagingTemplate messaging;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String mentor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mentor, null, List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));
    }

    @Test
    void issuesFrozenRanksWithOneAfterCommitRegistration() {
        String mentor = "eb54-pg-cert-mentor@example.org";
        var chain = auth.mentorship(mentor, Status.ACTIVE);
        var first = auth.enroll(chain.mentorship().getId(), "eb54-pg-cert-a@example.org").getStudent();
        var second = auth.enroll(chain.mentorship().getId(), "eb54-pg-cert-b@example.org").getStudent();
        var third = auth.enroll(chain.mentorship().getId(), "eb54-pg-cert-c@example.org").getStudent();
        points.saveAndFlush(TotalPoints.builder().mentorship(chain.mentorship()).student(first).totalPoints(10).build());
        points.saveAndFlush(TotalPoints.builder().mentorship(chain.mentorship()).student(second).totalPoints(10).build());
        points.saveAndFlush(TotalPoints.builder().mentorship(chain.mentorship()).student(third).totalPoints(5).build());
        authenticate(mentor);
        try {
            transactions.executeWithoutResult(status -> {
                var managedMentorship = entityManager.find(com.example.gradproj.EduNest.entity.mentorship.MentorShip.class,
                        chain.mentorship().getId());
                int before = TransactionSynchronizationManager.getSynchronizations().size();
                service.issueCertificates(chain.mentorship().getId());
                assertThat(entityManager.contains(managedMentorship)).isTrue();
                assertThat(TransactionSynchronizationManager.getSynchronizations().size() - before).isEqualTo(1);
                verify(messaging, never()).convertAndSendToUser(anyString(), eq("/queue/notifications"), any());
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select student_id, student_rank, points_at_issuance, "
                + "cohort_size_at_issuance from certificates where mentorship_id = ? order by student_rank", chain.mentorship().getId());
        assertThat(rows).hasSize(3);
        assertThat(((Number) rows.get(0).get("student_id")).longValue()).isEqualTo(first.getId());
        assertThat(((Number) rows.get(0).get("student_rank")).longValue()).isEqualTo(1);
        assertThat(((Number) rows.get(0).get("points_at_issuance")).intValue()).isEqualTo(10);
        assertThat(((Number) rows.get(1).get("cohort_size_at_issuance")).longValue()).isEqualTo(3);
        assertThat(((Number) rows.get(2).get("student_rank")).longValue()).isEqualTo(3);
        assertThat(((Number) rows.get(2).get("student_id")).longValue()).isEqualTo(third.getId());
        jdbc.update("update total_points set total_points = 99 where student_id = ? and mentorship_id = ?", first.getId(), chain.mentorship().getId());
        assertThat(jdbc.queryForObject("select points_at_issuance from certificates where student_id = ? and mentorship_id = ?",
                Integer.class, first.getId(), chain.mentorship().getId())).isEqualTo(10);
        verify(messaging, times(3)).convertAndSendToUser(anyString(), eq("/queue/notifications"), any());
    }

    @Test
    void rollbackLeavesNoCertificatesOrNotificationPushes() {
        String mentor = "eb54-pg-rollback-mentor@example.org";
        var chain = auth.mentorship(mentor, Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb54-pg-rollback-student@example.org").getStudent();
        points.saveAndFlush(TotalPoints.builder().mentorship(chain.mentorship()).student(student).totalPoints(3).build());
        authenticate(mentor);
        transactions.executeWithoutResult(status -> {
            service.issueCertificates(chain.mentorship().getId());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id = ?", Long.class,
                chain.mentorship().getId())).isZero();
        verify(messaging, never()).convertAndSendToUser(eq("eb54-pg-rollback-student@example.org"), anyString(), any());
    }

    @Test
    void historicalCertificatesExposeUnknownSnapshotValues() {
        var chain = auth.mentorship("eb54-historic-mentor@example.org", Status.ACTIVE);
        var student = auth.enroll(chain.mentorship().getId(), "eb54-historic-student@example.org").getStudent();
        certificates.saveAndFlush(Certificate.builder().student(student).mentorship(chain.mentorship())
                .rank(4).issuedAt(LocalDateTime.now()).build());
        var response = service.getStudentCertificates(student.getEmail(), 0, 10).getContent().get(0);
        assertThat(response.getRank()).isEqualTo(4);
        assertThat(response.getPointsAtIssuance()).isNull();
        assertThat(response.getCohortSizeAtIssuance()).isNull();
    }

    @Test
    void issuesTwelveThousandCandidatesWithSnapshotStableUnderConcurrentPointUpdates() throws Exception {
        String mentor = "eb54-scale-mentor@example.org";
        var chain = auth.mentorship(mentor, Status.ACTIVE);
        long mentorshipId = chain.mentorship().getId();
        Long studentRole = jdbc.queryForObject("select id from roles where name = 'STUDENT'", Long.class);
        assertThat(studentRole).isNotNull();
        int total = 12001;
        List<Object[]> userRows = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            userRows.add(new Object[]{"eb54-scale" + i + "@example.org", "Scale" + i, studentRole});
        }
        jdbc.batchUpdate("insert into users (deleted, enabled, created_at, updated_at, created_by, updated_by, "
                + "email, first_name, role_id) values (false, true, now(), now(), 'scale', 'scale', ?, ?, ?)", userRows);
        jdbc.update("insert into students (student_id, educational_level) select id, 'FRESH' from users "
                + "where email like 'eb54-scale%'");
        List<Object[]> enrollmentRows = new ArrayList<>();
        List<Object[]> pointsRows = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            enrollmentRows.add(new Object[]{mentorshipId, "eb54-scale" + i + "@example.org"});
            pointsRows.add(new Object[]{(i % 5) + 1, mentorshipId, "eb54-scale" + i + "@example.org"});
        }
        jdbc.batchUpdate("insert into enrollments (created_at, updated_at, created_by, updated_by, joined_at, mentorship_id, "
                + "student_id) select now(), now(), 'scale', 'scale', now(), ?, u.id from users u where u.email = ?",
                enrollmentRows);
        jdbc.batchUpdate("insert into total_points (created_at, updated_at, created_by, updated_by, total_points, "
                + "mentorship_id, student_id) select now(), now(), 'scale', 'scale', ?, ?, u.id "
                + "from users u where u.email = ?", pointsRows);

        AtomicBoolean stop = new AtomicBoolean(false);
        Thread churn = new Thread(() -> {
            while (!stop.get()) {
                jdbc.update("update total_points set total_points = total_points + 1 where mentorship_id = ?", mentorshipId);
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        });
        churn.start();
        long started = System.nanoTime();
        try {
            authenticate(mentor);
            transactions.executeWithoutResult(status -> service.issueCertificates(mentorshipId));
        } finally {
            stop.set(true);
            churn.join(15000);
            SecurityContextHolder.clearContext();
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        System.out.println("EB54 scale issuance wall time (ms): " + elapsedMs);

        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id = ?", Long.class, mentorshipId))
                .isEqualTo(total);
        List<Long> ranks = jdbc.queryForList("select student_rank from certificates where mentorship_id = ? order by student_rank",
                Long.class, mentorshipId);
        assertThat(ranks.size()).isEqualTo(total);
        assertThat(ranks.get(0)).isEqualTo(1L);
        assertThat(ranks.get(total - 1)).isEqualTo((long) total);
        assertThat(new HashSet<>(ranks)).hasSize(total);
        List<Integer> orderedPoints = jdbc.query("select points_at_issuance from certificates where mentorship_id = ? "
                        + "order by student_rank", (rs, n) -> rs.getInt(1), mentorshipId);
        for (int i = 1; i < orderedPoints.size(); i++) {
            assertThat(orderedPoints.get(i - 1)).as("rank " + i + " points must not trail lower ranks").isGreaterThanOrEqualTo(orderedPoints.get(i));
        }
        assertThat(jdbc.queryForObject("select count(distinct student_id) from certificates where mentorship_id = ?", Long.class, mentorshipId))
                .isEqualTo(total);
        assertThat(jdbc.queryForObject("select count(*) from user_notification where certificate_issuance_id is not null "
                + "and certificate_recipient_email like 'eb54-scale%'", Long.class))
                .isEqualTo(total);

        var payloads = ArgumentCaptor.forClass(Object.class);
        verify(messaging, times(total)).convertAndSendToUser(anyString(), eq("/queue/notifications"), payloads.capture());
        Set<UUID> deliveredIds = new HashSet<>();
        for (Object payload : payloads.getAllValues()) {
            NotificationDto dto = (NotificationDto) payload;
            assertThat(dto.getTitle()).isEqualTo("Certificate Issued!");
            assertThat(dto.getContent()).contains("rank #");
            assertThat(dto.getType()).isEqualTo(com.example.gradproj.EduNest.enums.notification.NotificationType.CERTIFICATE);
            assertThat(dto.isRead()).isFalse();
            assertThat(dto.getTime()).isNotNull();
            deliveredIds.add(dto.getId());
        }
        assertThat(deliveredIds).hasSize(total);
        assertThat(elapsedMs).isGreaterThan(0);
    }

    @Test
    void concurrentIssuanceRunsCreateCertificatesExactlyOnce() throws Exception {
        String mentor = "eb54-concurrent-mentor@example.org";
        var chain = auth.mentorship(mentor, Status.ACTIVE);
        long mentorshipId = chain.mentorship().getId();
        int students = 8;
        List<TotalPoints> rows = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            var student = auth.enroll(mentorshipId, "eb54-concurrent-" + i + "@example.org").getStudent();
            rows.add(TotalPoints.builder().mentorship(chain.mentorship()).student(student).totalPoints(100 - i).build());
        }
        points.saveAll(rows);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            try {
                authenticate(mentor);
                start.await();
                transactions.executeWithoutResult(status -> service.issueCertificates(mentorshipId));
                return "OK";
            } catch (Exception e) {
                return e.getClass().getSimpleName();
            }
        };
        Future<String> first = pool.submit(attempt);
        Future<String> second = pool.submit(attempt);
        start.countDown();
        String firstOutcome = first.get(90, TimeUnit.SECONDS);
        String secondOutcome = second.get(90, TimeUnit.SECONDS);
        pool.shutdownNow();
        SecurityContextHolder.clearContext();

        assertThat(firstOutcome + "|" + secondOutcome).contains("OK");
        long successes = (firstOutcome.equals("OK") ? 1 : 0) + (secondOutcome.equals("OK") ? 1 : 0);
        assertThat(successes).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from certificates where mentorship_id = ?", Long.class, mentorshipId))
                .isEqualTo(students);
        assertThat(jdbc.queryForObject("select count(distinct student_id) from certificates where mentorship_id = ?",
                Long.class, mentorshipId)).isEqualTo(students);
        assertThat(jdbc.queryForObject("select count(*) from user_notification where certificate_issuance_id is not null "
                + "and certificate_recipient_email like 'eb54-concurrent-%'", Long.class)).isEqualTo(students);
        verify(messaging, times(students)).convertAndSendToUser(anyString(), eq("/queue/notifications"), any(Object.class));
    }
}
