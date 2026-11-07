package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import com.example.gradproj.EduNest.dto.auth.TokenPairResponseDto;

public interface RefreshTokenService {

    /**
     * Issues an initial short-lived access JWT and a rotating refresh token upon successful login.
     */
    TokenPairResponseDto issueInitialTokens(PrincipalIdentity identity);

    /**
     * Atomically rotates a refresh token: consumes the presented token,
     * verifies single-use invariant, detects replays, and issues a new pair.
     */
    TokenPairResponseDto rotateRefreshToken(String rawRefreshToken);

    /**
     * Revokes a single refresh token session (client logout).
     */
    void logoutSingleSession(String rawRefreshToken);

    /**
     * Revokes all active refresh token sessions for the account bound to the provided access token.
     */
    void logoutAll(String accessToken);

    /**
     * Invalidation hook: revokes all active sessions for a user upon password change/reset.
     */
    void revokeAllForUser(Long userId);

    /**
     * Invalidation hook: revokes all active sessions for an admin upon password change/reset.
     */
    void revokeAllForAdmin(Long adminId);
}
