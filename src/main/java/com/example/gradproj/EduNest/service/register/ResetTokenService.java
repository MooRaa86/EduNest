package com.example.gradproj.EduNest.service.register;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/** Issues bearer credentials; only their versioned digests are stored. */
@Component
public class ResetTokenService {
    private static final String PREFIX = "v1:";
    private final SecureRandom random = new SecureRandom();
    private final Duration lifetime;

    public ResetTokenService(@Value("${app.auth.reset-token-ttl:PT5M}") Duration lifetime) {
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("app.auth.reset-token-ttl must be positive");
        }
        this.lifetime = lifetime;
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Duration lifetime() {
        return lifetime;
    }

    public String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean matches(String token, String stored) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}") || stored == null
                || !stored.matches("v1:[0-9a-f]{64}")) {
            return false;
        }
        // Reject alternate non-canonical Base64 encodings of the same bytes.
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token)) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(digest(token).getBytes(StandardCharsets.US_ASCII),
                stored.getBytes(StandardCharsets.US_ASCII));
    }
}
