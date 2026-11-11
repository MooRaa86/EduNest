package com.example.gradproj.EduNest.entity.email;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * EB-62 / INT-02: Outbox-style persistence entity for failed email deliveries.
 * Holds delivery metadata, error telemetry, attempt counts, and retry timestamps.
 */
@Entity
@Table(name = "failed_email_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FailedEmailAttempt extends BaseEntity {

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FailedEmailStatus status = FailedEmailStatus.PENDING;

    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;
}
