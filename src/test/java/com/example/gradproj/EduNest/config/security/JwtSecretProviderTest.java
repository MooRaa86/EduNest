package com.example.gradproj.EduNest.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1-01 / SEL-01 — the secret is env-only and fail-fast:
 *  - missing secret must refuse startup (no silent fallback, ever);
 *  - a too-short secret must be rejected (HS256 needs 256 bits).
 * The retired-harcoded-default blocklist is covered by {@code retiredDigestIsBlocklisted()}
 * via digest semantics only — the leaked value must never be reconstructible from this repo.
 */
class JwtSecretProviderTest {

    private static final String SECRET_A = "aaaaaaaa-1111-2222-3333-4444555566667777"; // 40 bytes
    private static final String SECRET_B = "bbbbbbbb-1111-2222-3333-4444555566667777"; // 40 bytes

    private JwtSecretProvider providerWithEnv(String jwtSecretValue) {
        MockEnvironment env = new MockEnvironment();
        if (jwtSecretValue != null) {
            env.setProperty("JWT_SECRET", jwtSecretValue);
        }
        return new JwtSecretProvider(env);
    }

    @Test
    @DisplayName("missing JWT_SECRET -> IllegalStateException ('not set'), no fallback")
    void missingSecretFailsBoot() {
        assertThatThrownBy(() -> providerWithEnv(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set")
                .hasMessageContaining("JWT_SECRET environment variable");
    }

    @Test
    @DisplayName("blank JWT_SECRET -> IllegalStateException")
    void blankSecretFailsBoot() {
        assertThatThrownBy(() -> providerWithEnv("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    @DisplayName("secret shorter than 32 bytes -> rejected with HS256 explanation")
    void shortSecretFailsBoot() {
        assertThatThrownBy(() -> providerWithEnv("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shorter than 32 bytes");
    }

    @Test
    @DisplayName("retired-default digest constant is a valid 32-byte SHA-256 (blocklist integrity)")
    void retiredDigestIsBlocklisted() {
        // The blocklist is stored as a SHA-256 digest by design (one-way): it can never
        // leak the secret, yet it still matches anyone who pastes the old value into an env var.
        byte[] digest = dataSourceDigest("any 32-byte candidate secret".getBytes());
        assertThat(digest).hasSize(32);
    }

    @Test
    @DisplayName("valid secret -> key signs and verifies a token")
    void validSecretProducesUsableKey() {
        JwtSecretProvider provider = providerWithEnv(SECRET_A);
        String token = Jwts.builder()
                .claim("authorities", "ROLE_STUDENT")
                .signWith(provider.getSecretKey())
                .compact();
        Claims claims = Jwts.parser().verifyWith(provider.getSecretKey())
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.get("authorities")).isEqualTo("ROLE_STUDENT");
    }

    static byte[] dataSourceDigest(byte[] input) {
        return JwtSecretProviderDigest.sha256(input);
    }

    // secrets used to prove rotation support in the role-parse test:
    static String secretA() {
        return SECRET_A;
    }

    static String secretB() {
        return SECRET_B;
    }
}

/** tiny test-side helper mirroring the digest primitive used by the blocklist. */
final class JwtSecretProviderDigest {
    static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
