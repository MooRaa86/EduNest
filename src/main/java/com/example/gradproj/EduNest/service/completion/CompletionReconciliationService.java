package com.example.gradproj.EduNest.service.completion;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompletionReconciliationService {
    private final JdbcTemplate jdbc;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void retryExhausted(UUID mentorshipId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new globalLogicEx("A reconciliation reason of up to 1000 characters is required");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        int updated = jdbc.update("""
            UPDATE mentorship_completion_job SET state='RETRY', attempt_count=0,
              next_attempt_at=clock_timestamp(), reconciled_at=clock_timestamp(), reconciled_by=?,
              reconciliation_reason=?, claim_generation=claim_generation+1, updated_at=clock_timestamp()
            WHERE mentorship_id=(SELECT id FROM mentorship WHERE public_id=?) AND state='EXHAUSTED'
            """, actor, reason, mentorshipId);
        if (updated != 1) throw new globalLogicEx("Completion job is not exhausted or does not exist");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                log.info("Completion job reconciliation mentorship={} actor={}", mentorshipId, actor.replaceAll("[\\r\\n]", "_"));
            }
        });
    }
}
