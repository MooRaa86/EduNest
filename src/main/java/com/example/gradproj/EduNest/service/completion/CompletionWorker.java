package com.example.gradproj.EduNest.service.completion;

import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Completion-specific durable worker. Each durable stage and its transition commit together. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.completion.worker.enabled", havingValue = "true", matchIfMissing = true)
public class CompletionWorker {
    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int leaseSeconds;

    record Claim(long id, UUID token, long generation) { }
    record Job(long id, long mentorshipId, String actor, String title, String mentorName, long cohort,
               String stage) { }
    record Student(long id, String email, Long rank) { }
    record AdminPush(String email, AdminNotificationResponse payload) { }
    record Admin(long id, String email) { }
    record UserPush(long id, String email, NotificationDto payload) { }

    public CompletionWorker(JdbcTemplate jdbc, SimpMessagingTemplate messaging, PlatformTransactionManager manager,
            @Value("${app.completion.worker.batch-size:100}") int batchSize,
            @Value("${app.completion.worker.lease-seconds:300}") int leaseSeconds) {
        this.jdbc = jdbc;
        this.messaging = messaging;
        this.tx = new TransactionTemplate(manager);
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.leaseSeconds = Math.max(30, leaseSeconds);
    }

    @Scheduled(fixedDelayString = "${app.completion.worker.poll-ms:30000}")
    public void poll() {
        Claim claim = tx.execute(status -> claim());
        if (claim == null) return;
        List<AdminPush> adminPushes;
        try {
            adminPushes = tx.execute(status -> process(claim));
        } catch (Exception ex) {
            log.error("Completion job={} failed", claim.id(), ex);
            tx.executeWithoutResult(status -> fail(claim, ex));
            return;
        }
        if (adminPushes != null) {
            try { deliver(claim.id(), adminPushes); }
            catch (Exception ex) { log.error("Completion job={} committed; notification push failed", claim.id(), ex); }
        }
    }

    private Claim claim() {
        jdbc.update("""
            WITH expired AS (
                SELECT id FROM mentorship_completion_job
                WHERE state='RUNNING' AND lease_until < clock_timestamp() AND attempt_count >= 5
                ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 50
            )
            UPDATE mentorship_completion_job j SET state='EXHAUSTED', claim_token=NULL, lease_until=NULL,
                last_error='Lease expired after final attempt', updated_at=clock_timestamp()
            FROM expired WHERE j.id=expired.id
            """);
        UUID token = UUID.randomUUID();
        List<Claim> claims = jdbc.query("""
            WITH candidate AS (
              SELECT id FROM mentorship_completion_job
              WHERE ((state IN ('PENDING','RETRY') AND next_attempt_at <= clock_timestamp())
                     OR (state='RUNNING' AND lease_until < clock_timestamp())) AND attempt_count < 5
              ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE mentorship_completion_job j SET state='RUNNING', claim_token=?,
                claim_generation=j.claim_generation+1, attempt_count=j.attempt_count+1,
                lease_until=clock_timestamp() + (? * interval '1 second'), updated_at=clock_timestamp()
            FROM candidate c WHERE j.id=c.id RETURNING j.id, j.claim_generation
            """, (rs, n) -> new Claim(rs.getLong(1), token, rs.getLong(2)), token, leaseSeconds);
        return claims.isEmpty() ? null : claims.get(0);
    }

    List<AdminPush> process(Claim claim) {
        List<Job> jobs = jdbc.query("""
            SELECT id, mentorship_id, actor_email, mentorship_title, mentor_name, cohort_size, stage
            FROM mentorship_completion_job
            WHERE id=? AND state='RUNNING' AND claim_token=? AND claim_generation=? FOR UPDATE
            """, (rs, n) -> new Job(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getLong(6), rs.getString(7)), claim.id(), claim.token(), claim.generation());
        if (jobs.isEmpty()) return null;
        Job job = jobs.get(0);
        if (job.stage().equals("CERTIFICATES")) {
            issue(job);
            jdbc.update("""
                UPDATE mentorship_completion_job SET stage='NOTIFICATIONS', state='PENDING', attempt_count=0,
                claim_token=NULL, lease_until=NULL, next_attempt_at=clock_timestamp(), last_error=NULL,
                updated_at=clock_timestamp() WHERE id=?
                """, job.id());
            return null;
        }
        if (!job.stage().equals("NOTIFICATIONS")) throw new IllegalStateException("Unexpected job stage");
        List<AdminPush> admin = notifications(job);
        jdbc.update("""
            UPDATE mentorship_completion_job SET stage='DONE', state='DONE', claim_token=NULL, lease_until=NULL,
                last_error=NULL, updated_at=clock_timestamp() WHERE id=?
            """, job.id());
        return admin;
    }

    private void issue(Job job) {
        long cursor = 0, count = 0;
        LocalDateTime issuedAt = LocalDateTime.now();
        while (true) {
            List<Student> page = jdbc.query("""
                SELECT student_id, recipient_email, student_rank FROM mentorship_completion_snapshot
                WHERE job_id=? AND student_rank > ? ORDER BY student_rank LIMIT ?
                """, (rs, n) -> new Student(rs.getLong(1), rs.getString(2), rs.getLong(3)), job.id(), cursor, batchSize);
            if (page.isEmpty()) break;
            for (Student student : page) {
                int inserted = jdbc.update("""
                    INSERT INTO certificates (student_id, mentorship_id, student_rank, points_at_issuance,
                      cohort_size_at_issuance, issued_at, created_at, updated_at, created_by, updated_by, public_id)
                    SELECT student_id, ?, student_rank, points_at_completion, cohort_size, ?, ?, ?, ?, ?, ?
                    FROM mentorship_completion_snapshot WHERE job_id=? AND student_id=?
                    """, job.mentorshipId(), issuedAt, issuedAt, issuedAt, job.actor(), job.actor(), UUID.randomUUID(),
                        job.id(), student.id());
                if (inserted != 1) throw new IllegalStateException("Missing completion snapshot row");
                count++;
            }
            cursor = page.get(page.size() - 1).rank();
        }
        if (count != job.cohort()) throw new IllegalStateException("Completion snapshot cohort mismatch");
    }

    private List<AdminPush> notifications(Job job) {
        UUID issuance = issuanceId(job.id());
        long cursor = 0;
        while (true) {
            List<Student> page = jdbc.query("""
                SELECT student_id, recipient_email, student_rank FROM mentorship_completion_snapshot
                WHERE job_id=? AND student_id > ? ORDER BY student_id LIMIT ?
                """, (rs, n) -> new Student(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)),
                job.id(), cursor, batchSize);
            if (page.isEmpty()) break;
            for (Student student : page) {
                if (student.rank() != null) {
                    createUserNotification(job, student, "Certificate Issued!",
                        "Congratulations! You earned a certificate in \"" + job.title() + "\" with rank #" + student.rank(),
                        "CERTIFICATE", issuance);
                }
            }
            cursor = page.get(page.size()-1).id();
        }
        Long notificationId = jdbc.queryForObject("""
            INSERT INTO notification (title, content, type, created_at, updated_at, created_by, updated_by, public_id)
            VALUES ('Mentorship Completed', ?, 'MENTORSHIP', now(), now(), ?, ?, ?) RETURNING id
            """, Long.class, "Your mentorship " + job.title() + " has been completed, You can now download your certificate, Best regards, EduNest Team",
            job.actor(), job.actor(), UUID.randomUUID());
        jdbc.update("""
            INSERT INTO user_notification (user_id, notification_id, is_read, created_at, updated_at,
                created_by, updated_by, public_id, certificate_issuance_id, certificate_recipient_email)
            SELECT student_id, ?, false, now(), now(), ?, ?, gen_random_uuid(), ?, recipient_email
            FROM mentorship_completion_snapshot WHERE job_id=? AND was_enrolled
            """, notificationId, job.actor(), job.actor(), issuance, job.id());
        List<AdminPush> pushes = new ArrayList<>();
        List<Admin> admins = jdbc.query("SELECT id, email FROM admin ORDER BY id",
                (rs, n) -> new Admin(rs.getLong(1), rs.getString(2)));
        for (Admin admin : admins) {
            String content = "Mentorship " + job.title() + " has been completed, Mentor : " + job.mentorName();
            LocalDateTime now = LocalDateTime.now();
            UUID publicId = jdbc.queryForObject("""
                INSERT INTO admin_notification (admin_id, title, content, type, created_at, updated_at,
                  created_by, updated_by, public_id) VALUES (?, 'Mentorship Completed', ?, 'MENTORSHIP', ?, ?, ?, ?, ?) RETURNING public_id
                """, UUID.class, admin.id(), content, now, now, job.actor(), job.actor(), UUID.randomUUID());
            pushes.add(new AdminPush(admin.email(), AdminNotificationResponse.builder().id(publicId)
                .title("Mentorship Completed").content(content).type(NotificationType.MENTORSHIP).createdAt(now).build()));
        }
        return pushes;
    }

    private void createUserNotification(Job job, Student student, String title, String body, String type, UUID issuance) {
        Long id = jdbc.queryForObject("""
            INSERT INTO notification (title, content, type, created_at, updated_at, created_by, updated_by, public_id)
            VALUES (?, ?, ?, now(), now(), ?, ?, ?) RETURNING id
            """, Long.class, title, body, type, job.actor(), job.actor(), UUID.randomUUID());
        jdbc.update("""
            INSERT INTO user_notification (user_id, notification_id, is_read, created_at, updated_at,
              created_by, updated_by, public_id, certificate_issuance_id, certificate_recipient_email)
            VALUES (?, ?, false, now(), now(), ?, ?, ?, ?, ?)
            """, student.id(), id, job.actor(), job.actor(), UUID.randomUUID(), issuance, student.email());
    }

    private void deliver(long jobId, List<AdminPush> admins) {
        UUID issuance = issuanceId(jobId);
        long cursor = 0;
        while (true) {
            List<UserPush> page = jdbc.query("""
                SELECT un.id, un.certificate_recipient_email, n.title, n.content, n.type, n.created_at, un.public_id
                FROM user_notification un JOIN notification n ON n.id=un.notification_id
                WHERE un.certificate_issuance_id=? AND un.id>? ORDER BY un.id LIMIT ?
                """, (rs, n) -> new UserPush(rs.getLong(1), rs.getString(2), NotificationDto.builder()
                    .id(rs.getObject(7, UUID.class)).title(rs.getString(3)).content(rs.getString(4))
                    .type(NotificationType.valueOf(rs.getString(5))).isRead(false)
                    .time(rs.getObject(6, LocalDateTime.class)).build()), issuance, cursor, batchSize);
            if (page.isEmpty()) break;
            for (UserPush push : page) {
                try { messaging.convertAndSendToUser(push.email(), "/queue/notifications", push.payload()); }
                catch (Exception ex) { log.warn("Completion push failed for notification {}", push.id(), ex); }
            }
            cursor = page.get(page.size()-1).id();
        }
        for (AdminPush admin : admins) {
            try { messaging.convertAndSendToUser(admin.email(), "/queue/notifications", admin.payload()); }
            catch (Exception ex) { log.warn("Completion admin push failed for job {}", jobId, ex); }
        }
    }

    private void fail(Claim claim, Exception error) {
        String summary = error.getClass().getSimpleName();
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                summary += " SQLSTATE=" + sql.getSQLState();
                break;
            }
            cause = cause.getCause();
        }
        jdbc.update("""
            UPDATE mentorship_completion_job SET state=CASE WHEN attempt_count >= 5 THEN 'EXHAUSTED' ELSE 'RETRY' END,
              next_attempt_at=clock_timestamp() + (LEAST(900, 60 * power(2, GREATEST(0, attempt_count-1))) * interval '1 second'),
              last_error=?, claim_token=NULL, lease_until=NULL, updated_at=clock_timestamp()
            WHERE id=? AND state='RUNNING' AND claim_token=? AND claim_generation=?
            """, summary, claim.id(), claim.token(), claim.generation());
    }

    private static UUID issuanceId(long id) {
        return UUID.nameUUIDFromBytes(("edunest-completion-" + id).getBytes(StandardCharsets.UTF_8));
    }
}
