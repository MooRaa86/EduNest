package com.example.gradproj.EduNest.service.completion;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompletionStatusService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public String status(long mentorshipId, String email) {
        List<String> current = jdbc.query("""
            SELECT CASE
                WHEN s.student_rank IS NULL THEN 'NOT_ELIGIBLE'
                WHEN j.stage <> 'CERTIFICATES' AND EXISTS (
                    SELECT 1 FROM certificates c WHERE c.mentorship_id=j.mentorship_id AND c.student_id=s.student_id
                ) THEN 'READY'
                WHEN j.stage='CERTIFICATES' AND j.state='EXHAUSTED' THEN 'FAILED'
                ELSE 'PENDING' END
            FROM mentorship_completion_job j
            JOIN mentorship_completion_snapshot s ON s.job_id=j.id
            JOIN users u ON u.id=s.student_id
            WHERE j.mentorship_id=? AND u.email=?
            """, (rs, n) -> rs.getString(1), mentorshipId, email);
        if (!current.isEmpty()) return current.get(0);
        // Legacy mentorships have no job. Only an existing certificate establishes readiness.
        Integer found = jdbc.queryForObject("""
            SELECT count(*) FROM certificates c JOIN users u ON u.id=c.student_id
            WHERE c.mentorship_id=? AND u.email=?
              AND NOT EXISTS (SELECT 1 FROM mentorship_completion_job j WHERE j.mentorship_id=c.mentorship_id)
            """, Integer.class, mentorshipId, email);
        if (found != null && found > 0) return "READY";
        throw new EntityNotFoundException("Certificate status not found");
    }
}
