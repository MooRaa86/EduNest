package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.utils.Constants;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Single owner of the JWT signing key. Validates at startup (constructor), so the app
 * refuses to boot without a usable secret — no silent fallback exists.
 *
 * Contract (P1-01 / SEL-01):
 *  - secret comes from the environment only ({@code JWT_SECRET});
 *  - minimum 32 bytes (HS256 / 256-bit requirement);
 *  - the previously-hardcoded default secret is rejected (matched by hash);
 *  - the key is created once and shared by every consumer (tokens stay verifiable
 *    across secret rotations because no other parser touches the raw secret).
 */
@Component
public class JwtSecretProvider {

    private static final String MIN_SECRET_SPEC = ">= 32 bytes (HS256 needs 256 bits)";
    private static final String CONFIG_HINT =
            "Provide it via the JWT_SECRET environment variable (never in properties files or constants).";

    private static final int SHA256_DIGEST_LENGTH = 32;

    /** SHA-256 hash of the retired hardcoded default — stored hashed by design (grep-audit rule). */
    private static final byte[] RETIRED_DEFAULT_HASH = hex(
            "a36acbfc5a334df66101b31b0efaf9daad20a86848324e275f1718e9b9941ca4"
    );

    private final SecretKey secretKey;

    public JwtSecretProvider(Environment env) {
        String secret = env.getProperty(Constants.JWT_SECRET_KEY);

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET is not set. " + MIN_SECRET_SPEC + ". " + CONFIG_HINT
            );
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET is shorter than 32 bytes (HS256 requires 256 bits). " + CONFIG_HINT
            );
        }
        if (matchesRetiredDefault(secret)) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET equals the retired hardcoded default — generate a fresh secret. "
                            + CONFIG_HINT
            );
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    private boolean matchesRetiredDefault(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Arrays.equals(digest, RETIRED_DEFAULT_HASH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for JWT_SECRET sanity check", e);
        }
    }

    private static byte[] hex(String hex) {
        if (hex.length() != 2 * SHA256_DIGEST_LENGTH) {
            throw new IllegalArgumentException("Malformed retired-secret digest constant");
        }
        byte[] out = new byte[SHA256_DIGEST_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
