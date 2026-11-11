package com.example.gradproj.EduNest.service.jwt;

import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import io.jsonwebtoken.Claims;

import javax.crypto.SecretKey;

public interface JwtServiceI {
    String generateToken();
    String generateAccessToken(PrincipalIdentity identity, String jti);
    void validateToken(String token);
    SecretKey getSecretKey();
    boolean isTokenValid(String token);
    String extractUserEmail(String token);
    String extractAuthorities(String token);
    Claims extractAllClaims(String token);
    String getFullName(String token);
    String extractUserId(String token);
    String extractJti(String token);
    String extractOwnerType(String token);
    Long extractOwnerId(String token);
}
