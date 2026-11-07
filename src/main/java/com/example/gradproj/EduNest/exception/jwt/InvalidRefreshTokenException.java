package com.example.gradproj.EduNest.exception.jwt;

/**
 * EB-91: Thrown when a refresh token is invalid, expired, revoked, replayed,
 * or belongs to a deactivated/deleted account.
 * Handled by GlobalExceptionHandler producing 401 ERR_INVALID_TOKEN.
 */
public class InvalidRefreshTokenException extends InvalidJwtToken {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
