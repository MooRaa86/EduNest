package com.example.gradproj.EduNest.repository.email;

import com.example.gradproj.EduNest.entity.email.FailedEmailAttempt;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * EB-62 / INT-02: Spring Data JPA repository for failed email attempt persistence and retries.
 */
@Repository
public interface FailedEmailAttemptRepository extends JpaRepository<FailedEmailAttempt, Long> {

    Optional<FailedEmailAttempt> findByPublicId(UUID publicId);

    @Query("""
        SELECT f FROM FailedEmailAttempt f
        WHERE f.status = com.example.gradproj.EduNest.entity.email.FailedEmailStatus.PENDING
          AND (f.nextRetryAt IS NULL OR f.nextRetryAt <= :now)
          AND f.attemptCount < f.maxAttempts
        ORDER BY f.id ASC
    """)
    List<FailedEmailAttempt> findPendingRetries(@Param("now") LocalDateTime now, Pageable pageable);

    Page<FailedEmailAttempt> findByStatus(FailedEmailStatus status, Pageable pageable);

    long countByStatus(FailedEmailStatus status);
}
