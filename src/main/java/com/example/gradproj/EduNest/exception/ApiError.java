package com.example.gradproj.EduNest.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * P1-05 / EB-12 — THE unified error envelope. Every client-facing error
 * (REST advice, security entry point, access-denied handler, JWT filter,
 * framework {@code /error}) is serialized in exactly this shape:
 *
 * <pre>{ "timestamp", "status", "error", "errorCode", "message", "path", "refId" }
 * + optional "fields" map for field-level validation (D-26 contract).</pre>
 *
 * {@code message} is always clean and safe to render directly in the FE;
 * internal details (stack traces, SQL, class names) are never present.
 * {@code refId} appears ONLY on sanitized 500s (correlates with server logs).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private LocalDateTime timestamp;
    private int status;
    private String error;       // HTTP reason phrase, e.g. "Forbidden"
    private String errorCode;   // stable machine code, e.g. "ERR_FORBIDDEN"
    private String message;     // clean, FE-renderable text
    private String path;
    private String refId;       // 500-only log-correlation id (D-26 / EB-12)
    private Map<String, String> fields; // D-26: field-level detail for ERR_VALIDATION & friends

    public static ApiError of(int status, String reason, ApiErrorCode code, String message, String path) {
        ApiError apiError = new ApiError();
        apiError.setTimestamp(LocalDateTime.now());
        apiError.setStatus(status);
        apiError.setError(reason);
        apiError.setErrorCode(code.name());
        apiError.setMessage(message);
        apiError.setPath(path);
        return apiError;
    }

    /** Adds the D-26 field-level detail map. */
    public ApiError withFields(Map<String, String> fields) {
        this.fields = fields;
        return this;
    }

    /** Attaches a 500 log-correlation id (must be logged by the caller too). */
    public ApiError withRefId(String refId) {
        this.refId = refId;
        return this;
    }
}
