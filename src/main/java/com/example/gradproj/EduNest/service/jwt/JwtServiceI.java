package com.example.gradproj.EduNest.service.jwt;

import io.jsonwebtoken.Claims;

import javax.crypto.SecretKey;

public interface JwtServiceI {
    String generateToken();
    void validateToken(String token);
    SecretKey getSecretKey();
    boolean isTokenValid(String token);
    String extractUsername(String token);
    String extractAuthorities(String token);
    Claims extractAllClaims(String token);
}
