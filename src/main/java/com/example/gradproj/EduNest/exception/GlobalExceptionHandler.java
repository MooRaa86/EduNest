package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.exception.registerExceptions.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler{

    private ResponseEntity<ErrorResponse> buildErrorResponse(String key, String message, HttpStatus status) {
        ErrorResponse response = new ErrorResponse();
        response.addError(key, message);
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        return buildErrorResponse("Email Error", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOtp(InvalidOtpException ex) {
        return buildErrorResponse("OTP Error", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(OtpExpiredException ex) {
        return buildErrorResponse("OTP Expiration", ex.getMessage(), HttpStatus.GONE);
    }

    @ExceptionHandler(UserAlreadyVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleUserVerified(UserAlreadyVerifiedException ex) {
        return buildErrorResponse("Verification Error", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse("User Search", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(RoleNotFoundException ex) {
        return buildErrorResponse("System Error", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(MethodArgumentNotValidException exception) {
        ErrorResponse response = new ErrorResponse();
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();

        for(FieldError fieldError : fieldErrors){
            response.addError(fieldError.getField(),fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().forEach(error -> {
            response.addError(error.getObjectName(), error.getDefaultMessage());
        });

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseException(HttpMessageNotReadableException ex) {
        ErrorResponse response = new ErrorResponse();

        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException ifx = (InvalidFormatException) ex.getCause();

            if (ifx.getTargetType() != null && ifx.getTargetType().isEnum()) {
                String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
                String rejectedValue = ifx.getValue().toString();
                String allowedValues = Arrays.toString(ifx.getTargetType().getEnumConstants());

                String errorMessage;
                if (rejectedValue.isEmpty()) {
                    errorMessage = "Value cannot be empty. Accepted values are: " + allowedValues;
                } else {
                    errorMessage = String.format("Invalid value '%s'. Accepted values are: %s", rejectedValue, allowedValues);
                }
                response.addError(fieldName, errorMessage);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        }

        response.addError("error", "Malformed JSON request or invalid data type");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(globalLogicEx.class)
    public ResponseEntity<ErrorResponse> handleLogicEx(globalLogicEx ex) {
        return buildErrorResponse("error", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidJwtToken.class)
    public ResponseEntity<ErrorResponse> handleInvalidJwtToken(InvalidJwtToken ex) {
        return buildErrorResponse("error", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(
            EntityNotFoundException ex
    ) {

        return buildErrorResponse("error","entity not found maybe id is invalid bro :)", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {

        Map<String, Object> response = new HashMap<>();

        String message = ex.getMostSpecificCause().getMessage();

        // Duplicate value
        if (message.contains("Duplicate entry")) {

            Pattern pattern = Pattern.compile("for key '(.+?)'");
            Matcher matcher = pattern.matcher(message);

            String field = "field";
            if (matcher.find()) {
                field = matcher.group(1);
            }

            response.put("error", field + " already exists.");
        }

        // Data too long
        else if (message.contains("Data too long")) {

            Pattern pattern = Pattern.compile("column '(.+?)'");
            Matcher matcher = pattern.matcher(message);

            String field = "field";
            if (matcher.find()) {
                field = matcher.group(1);
            }

            response.put("error", field + " exceeds the allowed length.");
        }

        // Cannot be null
        else if (message.contains("cannot be null")) {

            Pattern pattern = Pattern.compile("column '(.+?)'");
            Matcher matcher = pattern.matcher(message);

            String field = "field";
            if (matcher.find()) {
                field = matcher.group(1);
            }

            response.put("error", field + " is required.");
        }

        // Foreign key
        else if (message.contains("foreign key")) {

            response.put("error", "Invalid related data provided.");
        }

        else {
            response.put("error", "Database constraint violation occurred.");
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUserName(UsernameNotFoundException ex) {
        return buildErrorResponse("error", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredintials(BadCredentialsException ex) {
        return buildErrorResponse("error", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }


    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception exception){
        return buildErrorResponse("error", exception.getMessage(), HttpStatus.CONFLICT);
    }
}