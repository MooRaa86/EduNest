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
 * EB-62 / INT-02: Full detail response for administrative inspection, including html body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEmailDetailResponse {

    private UUID publicId;
    private String recipient;
    private String subject;
    private String htmlBody;
    private int attemptCount;
    private int maxAttempts;
    private String lastError;
    private FailedEmailStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime resolvedAt;
    private String resolvedBy;

    public static FailedEmailDetailResponse fromEntity(FailedEmailAttempt entity) {
        return FailedEmailDetailResponse.builder()
                .publicId(entity.getPublicId())
                .recipient(entity.getRecipient())
                .subject(entity.getSubject())
                .htmlBody(entity.getHtmlBody())
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
