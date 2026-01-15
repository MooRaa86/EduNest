package com.example.gradproj.EduNest.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception exception){
        return buildErrorResponse("Internal Server Error", exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
