package com.example.gradproj.EduNest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler{

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

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleException(Exception exception){
        ErrorResponse response = new ErrorResponse();
        response.addError("Error Message : ",exception.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
