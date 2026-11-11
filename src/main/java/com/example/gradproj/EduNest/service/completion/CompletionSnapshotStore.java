package com.example.gradproj.EduNest.service.completion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Creates the job and frozen ranking within the caller's completion transaction. */
@Component
@RequiredArgsConstructor
public class CompletionSnapshotStore {
    private final JdbcTemplate jdbc;

    public void create(long mentorshipId, String title, String actor, String mentorName) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Completion snapshot requires an active transaction");
        }
        Long jobId = jdbc.queryForObject("""
            INSERT INTO mentorship_completion_job (mentorship_id, actor_email, mentorship_title, mentor_name, cohort_size)
            VALUES (?, ?, ?, ?, 0) RETURNING id
            """, Long.class, mentorshipId, actor, title, mentorName);
        // Both the rank population and notification recipients use this one READ COMMITTED statement snapshot.
        jdbc.update("""
            INSERT INTO mentorship_completion_snapshot
                (job_id, student_id, recipient_email, was_enrolled, points_at_completion, student_rank, cohort_size)
            WITH ranked AS (
              SELECT tp.student_id, u.email, tp.total_points,
                     row_number() OVER (ORDER BY tp.total_points DESC, tp.student_id ASC) AS rank,
                     count(*) OVER () AS cohort
              FROM total_points tp JOIN users u ON u.id = tp.student_id
              WHERE tp.mentorship_id = ?
            ), enrolled AS (
              SELECT e.student_id, u.email FROM enrollments e JOIN users u ON u.id = e.student_id
              WHERE e.mentorship_id = ?
            )
            SELECT ?, COALESCE(r.student_id,e.student_id), COALESCE(r.email,e.email),
                   e.student_id IS NOT NULL, r.total_points, r.rank, r.cohort
            FROM ranked r FULL OUTER JOIN enrolled e ON e.student_id = r.student_id
            """, mentorshipId, mentorshipId, jobId);
        jdbc.update("""
            UPDATE mentorship_completion_job SET cohort_size =
              (SELECT count(*) FROM mentorship_completion_snapshot WHERE job_id = ? AND student_rank IS NOT NULL)
            WHERE id = ?
            """, jobId, jobId);
    }
}
