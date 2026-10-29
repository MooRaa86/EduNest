package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.exception.registerExceptions.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P1-05 / EB-12 — THE unified error contract. Every response is an
 * {@link ApiError} envelope: exact status + stable {@code errorCode} +
 * clean FE-renderable {@code message}. Zero technical leaks:
 * <ul>
 *   <li>the catch-all discards the exception message entirely and returns a
 *       sanitized generic 500 with a log-correlation {@code refId};</li>
 *   <li>data-layer messages (MySQL index/column identifiers) are replaced by
 *       fixed friendly text;</li>
 *   <li>security-layer messages are generalized (no Spring framework echo);</li>
 *   <li>field-level validation detail is KEPT (D-26 permanent contract) nested
 *       under {@code fields}.</li>
 * </ul>
 * Enumeration-neutrality of login failures is deliberately NOT here — it
 * lands with P1-08/EB-15 (SEL-03); only envelope wrapping happens in this PR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MSG_INTERNAL =
            "An unexpected system error occurred. Please try again.";
    private static final String MSG_FORBIDDEN =
            "You do not have permission to access this resource.";
    private static final String MSG_VALIDATION =
            "Please check the submitted fields.";

    private ResponseEntity<ApiError> build(HttpServletRequest request, HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), code, message, request.getRequestURI()));
    }

    /* ============ business / domain exceptions (message authored at throw site, FE-safe) ============ */

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(HttpServletRequest req, EmailAlreadyExistsException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Email is already registered." : ex.getMessage();
        return build(req, HttpStatus.CONFLICT, ApiErrorCode.ERR_EMAIL_IN_USE, message);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ApiError> handleInvalidOtp(HttpServletRequest req, InvalidOtpException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Invalid verification code." : ex.getMessage();
        return build(req, HttpStatus.BAD_REQUEST, ApiErrorCode.ERR_INVALID_OTP, message);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiError> handleOtpExpired(HttpServletRequest req, OtpExpiredException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Verification code has expired. Please request a new one." : ex.getMessage();
        return build(req, HttpStatus.GONE, ApiErrorCode.ERR_OTP_EXPIRED, message);
    }

    @ExceptionHandler(UserAlreadyVerifiedException.class)
    public ResponseEntity<ApiError> handleUserVerified(HttpServletRequest req, UserAlreadyVerifiedException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Account is already verified." : ex.getMessage();
        return build(req, HttpStatus.BAD_REQUEST, ApiErrorCode.ERR_ALREADY_VERIFIED, message);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(HttpServletRequest req, UserNotFoundException ex) {
        // fixed text — UserNotFound messages may mention addresses (EB-15 unifies enumeration wording)
        return build(req, HttpStatus.NOT_FOUND, ApiErrorCode.ERR_USER_NOT_FOUND, "User not found.");
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiError> handleRoleNotFound(HttpServletRequest req, RoleNotFoundException ex) {
        // server-side misconfiguration — do NOT reflect the missing role name to the client
        log.error("Role misconfiguration detected (500): {}", ex.getMessage());
        return build(req, HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.ERR_INTERNAL_SYSTEM, MSG_INTERNAL);
    }

    /* ============ request-shape exceptions ============ */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(HttpServletRequest req, MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        for (FieldError fieldError : fieldErrors) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ApiErrorCode.ERR_VALIDATION, MSG_VALIDATION, req.getRequestURI());
        body.setFields(fields.isEmpty() ? null : fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleJsonParseException(HttpServletRequest req, HttpMessageNotReadableException ex) {
        Map<String, String> fields = new LinkedHashMap<>();

        if (ex.getCause() instanceof InvalidFormatException ifx
                && ifx.getTargetType() != null && ifx.getTargetType().isEnum()) {
            String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
            String rejectedValue = ifx.getValue().toString();
            String allowedValues = Arrays.toString(ifx.getTargetType().getEnumConstants());

            String errorMessage = rejectedValue.isEmpty()
                    ? "Value cannot be empty. Accepted values are: " + allowedValues
                    : String.format("Invalid value '%s'. Accepted values are: %s", rejectedValue, allowedValues);
            if (fieldName != null) {
                fields.put(fieldName, errorMessage);
            } else {
                fields.put("value", errorMessage);
            }
        }

        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ApiErrorCode.ERR_MALFORMED_BODY, "Malformed JSON request or invalid data type.", req.getRequestURI());
        body.setFields(fields.isEmpty() ? null : fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<ApiError> handleUnknownField(HttpServletRequest req, UnrecognizedPropertyException ex) {
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ApiErrorCode.ERR_UNKNOWN_FIELD,
                "Field '" + ex.getPropertyName() + "' is not allowed.", req.getRequestURI());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("invalidField", ex.getPropertyName());
        fields.put("allowedFields", String.join(", ", ex.getKnownPropertyIds().stream()
                .map(Object::toString).sorted().toList()));
        body.setFields(fields);
        return ResponseEntity.badRequest().body(body);
    }

    /* ============ security exceptions ============ */

    @ExceptionHandler(InvalidJwtToken.class)
    public ResponseEntity<ApiError> handleInvalidJwtToken(HttpServletRequest req, InvalidJwtToken ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Invalid or expired token. Please log in again." : ex.getMessage();
        return build(req, HttpStatus.UNAUTHORIZED, ApiErrorCode.ERR_INVALID_TOKEN, message);
    }

    /**
     * Method-security denials (@PreAuthorize / service-level checks) — today
     * these fall into the catch-all as a 409; they are authorization outcomes
     * and must be 403. (Filter-chain denials never reach this advice — they
     * are handled by EduNestAccessDeniedHandler with the same shape/code.)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest req, AccessDeniedException ex) {
        return build(req, HttpStatus.FORBIDDEN, ApiErrorCode.ERR_FORBIDDEN, MSG_FORBIDDEN);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> handleInvalidUserName(HttpServletRequest req, UsernameNotFoundException ex) {
        // fixed text — email echo retired (enumeration wording unification: EB-15)
        return build(req, HttpStatus.NOT_FOUND, ApiErrorCode.ERR_USER_NOT_FOUND, "User not found.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(HttpServletRequest req, BadCredentialsException ex) {
        // fixed text — enumeration-neutral by construction (EB-15 builds on this)
        return build(req, HttpStatus.UNAUTHORIZED, ApiErrorCode.ERR_INVALID_CREDENTIALS, "Invalid email or password.");
    }

    /* ============ data-layer exceptions (identifiers stripped, per EB-12 sanitize policy) ============ */

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(HttpServletRequest req, EntityNotFoundException ex) {
        return build(req, HttpStatus.NOT_FOUND, ApiErrorCode.ERR_RESOURCE_NOT_FOUND,
                "The requested item was not found.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingStaticResource(HttpServletRequest req, NoResourceFoundException ex) {
        return build(req, HttpStatus.NOT_FOUND, ApiErrorCode.ERR_RESOURCE_NOT_FOUND,
                "The requested item was not found.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(HttpServletRequest req, DataIntegrityViolationException ex) {
        String message = "The submitted data conflicts with an existing record.";
        try {
            String raw = ex.getMostSpecificCause().getMessage();
            log.warn("Data integrity violation (400, friendly text): {}", raw);
            if (raw != null) {
                String lower = raw.toLowerCase();
                if (lower.contains("duplicate entry")) {
                    message = "This value is already in use.";
                } else if (lower.contains("data too long")) {
                    message = "One of the submitted values is too long.";
                } else if (lower.contains("cannot be null")) {
                    message = "Some required information is missing.";
                } else if (lower.contains("foreign key")) {
                    message = "Invalid related data provided.";
                }
            }
        } catch (Exception parseFailure) {
            // keep the generic message — never surface DB parsing problems
        }
        return build(req, HttpStatus.BAD_REQUEST, ApiErrorCode.ERR_CONSTRAINT_VIOLATION, message);
    }

    /* ============ business logic + catch-all ============ */

    @ExceptionHandler(globalLogicEx.class)
    public ResponseEntity<ApiError> handleLogicEx(HttpServletRequest req, globalLogicEx ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "The requested operation is not allowed." : ex.getMessage();
        return build(req, HttpStatus.BAD_REQUEST, ApiErrorCode.ERR_BUSINESS_RULE, message);
    }

    /**
     * THE FLIP (SEL-14/E1): everything unexpected → sanitized 500. The raw
     * exception message is logged (with a correlation refId) and NEVER sent
     * to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleCatchAll(HttpServletRequest req, Exception exception) {
        String refId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [refId={}] on {} {}", refId, req.getMethod(), req.getRequestURI(), exception);
        ApiError body = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ApiErrorCode.ERR_INTERNAL_SYSTEM, MSG_INTERNAL, req.getRequestURI());
        body.setRefId(refId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
