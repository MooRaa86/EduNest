package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.repository.auth.RefreshTokenSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * EB-91: Transactional operations that must commit independently of caller rollback.
 * Used for immediate, persistent replay revocation across an entire session family.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenTx {

    private final RefreshTokenSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyImmediately(String familyId, String reason) {
        log.warn("EB-91: Revoking entire session family {} with reason: {}", familyId, reason);
        sessionRepository.revokeFamily(familyId, Instant.now(), reason);
    }
}
