package com.example.gradproj.EduNest.service.email;

import com.example.gradproj.EduNest.dto.email.FailedEmailDetailResponse;
import com.example.gradproj.EduNest.dto.email.FailedEmailResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.email.FailedEmailAttempt;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import jakarta.persistence.EntityNotFoundException;
import com.example.gradproj.EduNest.repository.email.FailedEmailAttemptRepository;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * EB-62 / INT-02: Scheduled and administrative worker service for retrying failed emails
 * and providing operational observability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailRetryService {

    private final FailedEmailAttemptRepository failedEmailRepo;
    private final EmailService emailService;

    /**
     * Periodic scheduled worker that polls pending failed emails due for retry.
     */
    @Scheduled(fixedDelayString = "${app.email.retry.fixed-delay-ms:60000}")
    @Transactional
    public void retryPendingEmails() {
        LocalDateTime now = LocalDateTime.now();
        List<FailedEmailAttempt> pending = failedEmailRepo.findPendingRetries(now, PageRequest.of(0, 20));
        if (pending.isEmpty()) {
            return;
        }

        log.info("EB-62: Found {} pending emails due for retry at {}", pending.size(), now);
        for (FailedEmailAttempt attempt : pending) {
            retrySingleAttempt(attempt, now);
        }
    }

    /**
     * Re-attempt delivery of a single failed email record.
     */
    @Transactional
    public boolean retrySingleAttempt(FailedEmailAttempt attempt, LocalDateTime now) {
        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        attempt.setLastAttemptAt(now);

        try {
            emailService.sendDirect(attempt.getRecipient(), attempt.getSubject(), attempt.getHtmlBody());
            attempt.setStatus(FailedEmailStatus.DELIVERED);
            attempt.setNextRetryAt(null);
            failedEmailRepo.save(attempt);
            log.info("EB-62: Successfully retried email id={} to {}", attempt.getId(), attempt.getRecipient());
            return true;
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (errorMsg.length() > 2000) {
                errorMsg = errorMsg.substring(0, 2000);
            }
            attempt.setLastError(errorMsg);

            if (attempt.getAttemptCount() >= attempt.getMaxAttempts()) {
                attempt.setStatus(FailedEmailStatus.EXHAUSTED);
                attempt.setNextRetryAt(null);
                log.warn("EB-62: Email id={} to {} reached max attempts ({}) and is now EXHAUSTED",
                        attempt.getId(), attempt.getRecipient(), attempt.getMaxAttempts());
            } else {
                long delayMinutes = (attempt.getAttemptCount() == 2) ? 5 : 15;
                attempt.setNextRetryAt(now.plusMinutes(delayMinutes));
                log.info("EB-62: Email id={} to {} failed attempt {}. Next retry at {}",
                        attempt.getId(), attempt.getRecipient(), attempt.getAttemptCount(), attempt.getNextRetryAt());
            }
            failedEmailRepo.save(attempt);
            return false;
        }
    }

    /**
     * Administrative query for paginated failed email records.
     */
    @Transactional(readOnly = true)
    public PageResponse<FailedEmailResponse> getFailedEmails(FailedEmailStatus status, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);

        PageRequest pageRequest = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FailedEmailAttempt> attemptsPage;

        if (status != null) {
            attemptsPage = failedEmailRepo.findByStatus(status, pageRequest);
        } else {
            attemptsPage = failedEmailRepo.findAll(pageRequest);
        }

        List<FailedEmailResponse> dtoList = attemptsPage.getContent().stream()
                .map(FailedEmailResponse::fromEntity)
                .toList();

        return PageResponse.<FailedEmailResponse>builder()
                .content(dtoList)
                .page(attemptsPage.getNumber())
                .size(attemptsPage.getSize())
                .totalElements(attemptsPage.getTotalElements())
                .totalPages(attemptsPage.getTotalPages())
                .build();
    }

    /**
     * Administrative inspection of a specific failed email with full html body.
     */
    @Transactional(readOnly = true)
    public FailedEmailDetailResponse getFailedEmailDetail(UUID publicId) {
        FailedEmailAttempt attempt = failedEmailRepo.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Failed email attempt not found for id: " + publicId));
        return FailedEmailDetailResponse.fromEntity(attempt);
    }

    /**
     * Administrative manual retry trigger.
     */
    @Transactional
    public boolean manualRetry(UUID publicId, String adminEmail) {
        FailedEmailAttempt attempt = failedEmailRepo.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Failed email attempt not found for id: " + publicId));

        LocalDateTime now = LocalDateTime.now();
        boolean success = retrySingleAttempt(attempt, now);
        if (success) {
            attempt.setResolvedAt(now);
            attempt.setResolvedBy(adminEmail != null ? adminEmail : "ADMIN");
            failedEmailRepo.save(attempt);
        }
        return success;
    }

    /**
     * Administrative manual resolution/dismissal without re-sending.
     */
    @Transactional
    public void resolve(UUID publicId, String adminEmail) {
        FailedEmailAttempt attempt = failedEmailRepo.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Failed email attempt not found for id: " + publicId));

        attempt.setStatus(FailedEmailStatus.RESOLVED);
        attempt.setNextRetryAt(null);
        attempt.setResolvedAt(LocalDateTime.now());
        attempt.setResolvedBy(adminEmail != null ? adminEmail : "ADMIN");
        failedEmailRepo.save(attempt);
        log.info("EB-62: Email id={} to {} manually marked RESOLVED by {}",
                attempt.getId(), attempt.getRecipient(), attempt.getResolvedBy());
    }
}
