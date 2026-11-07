package com.example.gradproj.EduNest.dto.email;

import com.example.gradproj.EduNest.entity.email.FailedEmailAttempt;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EB-62 / INT-02: Summary response for administrative failed email listing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEmailResponse {

    private UUID publicId;
    private String recipient;
    private String subject;
    private int attemptCount;
    private int maxAttempts;
    private String lastError;
    private FailedEmailStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime resolvedAt;
    private String resolvedBy;

    public static FailedEmailResponse fromEntity(FailedEmailAttempt entity) {
        return FailedEmailResponse.builder()
                .publicId(entity.getPublicId())
                .recipient(entity.getRecipient())
                .subject(entity.getSubject())
                .attemptCount(entity.getAttemptCount())
                .maxAttempts(entity.getMaxAttempts())
                .lastError(entity.getLastError())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .lastAttemptAt(entity.getLastAttemptAt())
                .nextRetryAt(entity.getNextRetryAt())
                .resolvedAt(entity.getResolvedAt())
                .resolvedBy(entity.getResolvedBy())
                .build();
    }
}
