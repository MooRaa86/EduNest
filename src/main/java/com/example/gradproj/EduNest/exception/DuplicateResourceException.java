package com.example.gradproj.EduNest.exception;

/**
 * P3-02 / EB-44 — thrown at the service layer as the semantic translation of a
 * uniqueness violation (SQLSTATE 23505 / {@code uk_*} constraint). Rendered as
 * HTTP 409 CONFLICT with {@link ApiErrorCode#ERR_DUPLICATE_RESOURCE} by
 * {@link GlobalExceptionHandler#handleDuplicateResource}. Message is authored
 * at the throw site and is FE-safe.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
