package com.example.gradproj.EduNest.exception;

/**
 * P1-05 / EB-12 — stable, machine-readable error codes for the unified error
 * envelope ({@link ApiError}). The FE parses {@code errorCode}; the human
 * text lives in the envelope's {@code message} and is authored at the
 * handler layer (clean, user-facing, never internal details).
 */
public enum ApiErrorCode {

    ERR_UNAUTHENTICATED,
    ERR_INVALID_TOKEN,
    ERR_FORBIDDEN,
    ERR_VALIDATION,
    ERR_MALFORMED_BODY,
    ERR_UNKNOWN_FIELD,
    ERR_BUSINESS_RULE,
    ERR_RESOURCE_NOT_FOUND,
    ERR_USER_NOT_FOUND,
    ERR_INVALID_CREDENTIALS,
    ERR_EMAIL_IN_USE,
    ERR_ALREADY_VERIFIED,
    ERR_INVALID_OTP,
    ERR_OTP_EXPIRED,
    ERR_CONSTRAINT_VIOLATION,
    ERR_INTERNAL_SYSTEM
}
