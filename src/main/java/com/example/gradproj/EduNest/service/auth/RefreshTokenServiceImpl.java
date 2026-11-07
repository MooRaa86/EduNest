package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import com.example.gradproj.EduNest.dto.auth.TokenPairResponseDto;
import com.example.gradproj.EduNest.entity.auth.RefreshTokenSession;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.jwt.InvalidRefreshTokenException;
import com.example.gradproj.EduNest.repository.auth.RefreshTokenSessionRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.jwt.JwtServiceI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int SLIDING_EXPIRATION_DAYS = 7;
    private static final int ABSOLUTE_EXPIRATION_DAYS = 30;

    private final RefreshTokenSessionRepository sessionRepository;
    private final RefreshTokenTx refreshTokenTx;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final JwtServiceI jwtService;

    @Override
    @Transactional
    public TokenPairResponseDto issueInitialTokens(PrincipalIdentity identity) {
        UserEntity user = null;
        Admin admin = null;
        String role;

        if (identity.type() == PrincipalIdentity.Type.USER) {
            user = userRepository.findById(identity.id())
                    .orElseThrow(() -> new InvalidRefreshTokenException("User account not found"));
            if (!user.isEnabled() || user.isDeleted()) {
                throw new InvalidRefreshTokenException("User account is deactivated or deleted");
            }
            role = "ROLE_" + user.getRole().getName();
        } else {
            admin = adminRepository.findById(identity.id())
                    .orElseThrow(() -> new InvalidRefreshTokenException("Admin account not found"));
            role = "ROLE_" + admin.getRole().getName();
        }

        String familyId = UUID.randomUUID().toString();
        String rawToken = RefreshTokenCryptoUtil.generateRawToken();
        String tokenHash = RefreshTokenCryptoUtil.hashToken(rawToken);
        String jti = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(SLIDING_EXPIRATION_DAYS, ChronoUnit.DAYS);
        Instant familyExpiresAt = now.plus(ABSOLUTE_EXPIRATION_DAYS, ChronoUnit.DAYS);

        RefreshTokenSession session = RefreshTokenSession.builder()
                .familyId(familyId)
                .tokenHash(tokenHash)
                .accessTokenJti(jti)
                .user(user)
                .admin(admin)
                .expiresAt(expiresAt)
                .familyExpiresAt(familyExpiresAt)
                .revoked(false)
                .build();

        sessionRepository.save(session);

        String accessToken = jwtService.generateAccessToken(identity, jti);

        return TokenPairResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(rawToken)
                .role(role)
                .build();
    }

    @Override
    @Transactional
    public TokenPairResponseDto rotateRefreshToken(String rawRefreshToken) {
        String hash = RefreshTokenCryptoUtil.hashToken(rawRefreshToken);
        RefreshTokenSession session = sessionRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        // Replay Detection: Attempting to use an already revoked token revokes the entire family
        if (session.isRevoked()) {
            refreshTokenTx.revokeFamilyImmediately(session.getFamilyId(), "REPLAY_DETECTED");
            throw new InvalidRefreshTokenException("Invalid refresh token: token reuse detected, session terminated");
        }

        Instant now = Instant.now();
        if (session.isExpired(now)) {
            session.setRevoked(true);
            session.setRevokedAt(now);
            session.setRevokedReason("EXPIRED");
            sessionRepository.save(session);
            throw new InvalidRefreshTokenException("Refresh token has expired, please login again");
        }

        // Account status check
        String role;
        PrincipalIdentity identity;
        if (session.getUser() != null) {
            UserEntity user = session.getUser();
            if (!user.isEnabled() || user.isDeleted()) {
                throw new InvalidRefreshTokenException("User account is deactivated or deleted");
            }
            role = "ROLE_" + user.getRole().getName();
            identity = new PrincipalIdentity(PrincipalIdentity.Type.USER, user.getId(), user.getEmail());
        } else if (session.getAdmin() != null) {
            Admin admin = session.getAdmin();
            role = "ROLE_" + admin.getRole().getName();
            identity = new PrincipalIdentity(PrincipalIdentity.Type.ADMIN, admin.getId(), admin.getEmail());
        } else {
            throw new InvalidRefreshTokenException("Session has no associated account owner");
        }

        // Single-use rotation: revoke current session
        String newRawToken = RefreshTokenCryptoUtil.generateRawToken();
        String newHash = RefreshTokenCryptoUtil.hashToken(newRawToken);
        String newJti = UUID.randomUUID().toString();

        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason("ROTATED");
        session.setReplacedByHash(newHash);
        sessionRepository.save(session);

        // Calculate sliding expiry capped by absolute family expiry
        Instant slidingExpiry = now.plus(SLIDING_EXPIRATION_DAYS, ChronoUnit.DAYS);
        if (slidingExpiry.isAfter(session.getFamilyExpiresAt())) {
            slidingExpiry = session.getFamilyExpiresAt();
        }

        RefreshTokenSession newSession = RefreshTokenSession.builder()
                .familyId(session.getFamilyId())
                .tokenHash(newHash)
                .accessTokenJti(newJti)
                .user(session.getUser())
                .admin(session.getAdmin())
                .expiresAt(slidingExpiry)
                .familyExpiresAt(session.getFamilyExpiresAt())
                .revoked(false)
                .build();

        sessionRepository.save(newSession);

        String newAccessToken = jwtService.generateAccessToken(identity, newJti);

        return TokenPairResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawToken)
                .role(role)
                .build();
    }

    @Override
    @Transactional
    public void logoutSingleSession(String rawRefreshToken) {
        String hash = RefreshTokenCryptoUtil.hashToken(rawRefreshToken);
        sessionRepository.findByTokenHash(hash).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.setRevoked(true);
                session.setRevokedAt(Instant.now());
                session.setRevokedReason("USER_LOGOUT");
                sessionRepository.save(session);
            }
        });
    }

    @Override
    @Transactional
    public void logoutAll(String accessToken) {
        String jti = jwtService.extractJti(accessToken);
        String ownerType = jwtService.extractOwnerType(accessToken);
        Long ownerId = jwtService.extractOwnerId(accessToken);

        if (jti == null || ownerType == null || ownerId == null) {
            throw new InvalidRefreshTokenException("Token is not bound to a valid session");
        }

        // Verify that this JTI corresponds to an active or valid session
        sessionRepository.findByAccessTokenJti(jti)
                .orElseThrow(() -> new InvalidRefreshTokenException("Session binding not found or already invalidated"));

        Instant now = Instant.now();
        if ("USER".equalsIgnoreCase(ownerType)) {
            sessionRepository.revokeAllByUserId(ownerId, now, "LOGOUT_ALL");
        } else if ("ADMIN".equalsIgnoreCase(ownerType)) {
            sessionRepository.revokeAllByAdminId(ownerId, now, "LOGOUT_ALL");
        } else {
            throw new InvalidRefreshTokenException("Unknown account owner type in token");
        }
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        if (userId != null) {
            sessionRepository.revokeAllByUserId(userId, Instant.now(), "PASSWORD_RESET");
        }
    }

    @Override
    @Transactional
    public void revokeAllForAdmin(Long adminId) {
        if (adminId != null) {
            sessionRepository.revokeAllByAdminId(adminId, Instant.now(), "PASSWORD_RESET");
        }
    }
}
