package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.repository.auth.RefreshTokenSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * EB-91: Periodic cleanup of expired refresh token sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupService {

    private final RefreshTokenSessionRepository sessionRepository;

    /**
     * Runs daily at midnight UTC to purge expired sessions.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        int deletedCount = sessionRepository.deleteExpiredSessions(now);
        if (deletedCount > 0) {
            log.info("EB-91 Cleanup: Purged {} expired refresh token sessions", deletedCount);
        }
    }
}
