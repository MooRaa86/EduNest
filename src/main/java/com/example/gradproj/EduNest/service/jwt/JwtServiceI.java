package com.example.gradproj.EduNest.service.jwt;

import javax.crypto.SecretKey;

public interface JwtServiceI {
    String generateToken();
    void validateToken(String token);
    SecretKey getSecretKey();
}
