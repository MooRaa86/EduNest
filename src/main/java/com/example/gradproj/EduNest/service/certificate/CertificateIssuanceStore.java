package com.example.gradproj.EduNest.service.certificate;

import com.example.gradproj.EduNest.config.transaction.AfterCommitExecutor;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Certificate-specific JDBC writes share the caller's transaction without retaining JPA entities. */
@Slf4j
@Component
public class CertificateIssuanceStore {
    private final JdbcTemplate jdbc;
    private final AfterCommitExecutor afterCommit;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate committedReads;
    private final int batchSize;

    public CertificateIssuanceStore(JdbcTemplate jdbc, AfterCommitExecutor afterCommit,
            SimpMessagingTemplate messaging, PlatformTransactionManager manager,
            @Value("${app.certificate.issuance-batch-size:100}") int batchSize) {
        this.jdbc = jdbc;
        this.afterCommit = afterCommit;
        this.messaging = messaging;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.committedReads = new TransactionTemplate(manager);
        this.committedReads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.committedReads.setReadOnly(true);
    }

    record Candidate(long studentId, String email, int points, long rank, long cohort) { }
    record Delivery(long id, String email, NotificationDto payload) { }

    public void issue(long mentorshipId, String title, String actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Certificate issuance requires an active transaction");
        }
        UUID issuance = UUID.randomUUID();
        String table = "cert_snapshot_" + issuance.toString().replace("-", "");
        // PostgreSQL READ COMMITTED gives this statement a single MVCC snapshot. No live leaderboard
        // reads are used after materialization; ON COMMIT DROP also handles rollback.
        boolean postgres = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) c ->
                c.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
        jdbc.execute("CREATE " + (postgres ? "TEMPORARY" : "LOCAL TEMPORARY") + " TABLE " + table
                + (postgres ? " ON COMMIT DROP" : "") + " AS "
                + "SELECT tp.student_id, u.email, tp.total_points AS points, "
                + "row_number() over (order by tp.total_points desc, tp.student_id asc) AS rank, "
                + "count(*) over () AS cohort FROM total_points tp JOIN users u ON u.id = tp.student_id "
                + "WHERE tp.mentorship_id = " + mentorshipId);
        jdbc.execute("CREATE UNIQUE INDEX " + table + "_rank ON " + table + " (rank)");
        LocalDateTime issuedAt = LocalDateTime.now();
        long rank = 0;
        int issued = 0;
        while (true) {
            List<Candidate> page = jdbc.query("SELECT student_id, email, points, rank, cohort FROM " + table
                            + " WHERE rank > ? ORDER BY rank LIMIT ?",
                    (rs, n) -> new Candidate(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getLong(5)),
                    rank, batchSize);
            if (page.isEmpty()) break;
            for (Candidate row : page) {
                jdbc.update("INSERT INTO certificates (student_id, mentorship_id, student_rank, points_at_issuance, "
                                + "cohort_size_at_issuance, issued_at, created_at, updated_at, created_by, updated_by, public_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", row.studentId(), mentorshipId, row.rank(),
                        row.points(), row.cohort(), issuedAt, issuedAt, issuedAt, actor, actor, UUID.randomUUID());
                String body = "Congratulations! You earned a certificate in \"" + title + "\" with rank #" + row.rank();
                LocalDateTime notificationTime = LocalDateTime.now();
                var key = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement("INSERT INTO notification (title, content, type, created_at, "
                            + "updated_at, created_by, updated_by, public_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new String[]{"id"});
                    statement.setString(1, "Certificate Issued!");
                    statement.setString(2, body);
                    statement.setString(3, "CERTIFICATE");
                    statement.setObject(4, notificationTime);
                    statement.setObject(5, notificationTime);
                    statement.setString(6, actor);
                    statement.setString(7, actor);
                    statement.setObject(8, UUID.randomUUID());
                    return statement;
                }, key);
                Long notificationId = key.getKey().longValue();
                jdbc.update("INSERT INTO user_notification (user_id, notification_id, is_read, created_at, updated_at, "
                                + "created_by, updated_by, public_id, certificate_issuance_id, certificate_recipient_email) "
                                + "VALUES (?, ?, false, ?, ?, ?, ?, ?, ?, ?)", row.studentId(), notificationId,
                        notificationTime, notificationTime, actor, actor, UUID.randomUUID(), issuance, row.email());
                issued++;
            }
            rank = page.get(page.size() - 1).rank();
        }
        if (!postgres) jdbc.execute("DROP TABLE " + table);
        if (issued > 0) afterCommit.execute(() -> deliver(issuance));
    }

    private void deliver(UUID issuance) {
        long cursor = 0;
        while (true) {
            final long after = cursor;
            List<Delivery> page = committedReads.execute(status -> jdbc.query(
                    "SELECT un.id, un.certificate_recipient_email, n.title, n.content, n.type, n.created_at, un.public_id "
                            + "FROM user_notification un JOIN notification n ON n.id = un.notification_id "
                            + "WHERE un.certificate_issuance_id = ? AND un.id > ? ORDER BY un.id LIMIT ?",
                    (rs, n) -> new Delivery(rs.getLong(1), rs.getString(2), NotificationDto.builder()
                            .id(rs.getObject(7, UUID.class)).title(rs.getString(3)).content(rs.getString(4))
                            .type(NotificationType.valueOf(rs.getString(5))).isRead(false)
                            .time(rs.getObject(6, LocalDateTime.class)).build()), issuance, after, batchSize));
            if (page == null || page.isEmpty()) return;
            for (Delivery delivery : page) {
                try {
                    messaging.convertAndSendToUser(delivery.email(), "/queue/notifications", delivery.payload());
                } catch (Exception ex) {
                    log.error("Error executing post-commit certificate notification", ex);
                }
            }
            cursor = page.get(page.size() - 1).id();
        }
    }
}
