package com.example.gradproj.EduNest.exception.jwt;

public class InvalidJwtToken extends RuntimeException {
    public InvalidJwtToken(String message) {
        super(message);
    }
}
