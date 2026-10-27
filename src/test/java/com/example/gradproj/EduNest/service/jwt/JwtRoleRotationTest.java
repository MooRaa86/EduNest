package com.example.gradproj.EduNest.service.jwt;

import com.example.gradproj.EduNest.config.security.JwtSecretProvider;
import com.example.gradproj.EduNest.service.auth.LoginServiceI;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-01 / SEL-01 — role parsing must work with ANY secret value.
 * Old defect: LoginServiceI.getJwtRole re-parsed tokens with the hardcoded default,
 * so rotating the secret broke the role claim silently. Now a single parser
 * (JwtService -> JwtSecretProvider) serves generation, validation and roles.
 */
class JwtRoleRotationTest {

    private static final String SECRET_A = "aaaaaaaa-1111-2222-3333-4444555566667777"; // 40 bytes
    private static final String SECRET_B = "bbbbbbbb-1111-2222-3333-4444555566667777"; // 40 bytes

    private JwtService jwtServiceWithSecret(String secret) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("JWT_SECRET", secret);
        return new JwtService(null, null, new JwtSecretProvider(env)); // repositories unused for claim reads
    }

    private String tokenSignedWith(String secret, String authorities) {
        return Jwts.builder()
                .claim("username", "student1@edunest.local")
                .claim("authorities", authorities)
                .claim("fullName", "Test Student")
                .signWith(jwtServiceWithSecret(secret).getSecretKey())
                .compact();
    }

    @Test
    @DisplayName("role claim parses under secret A AND under a rotated secret B")
    void roleParseSurvivesSecretRotation() {
        String authorities = "ROLE_STUDENT,ROLE_USER";
        String tokenA = tokenSignedWith(SECRET_A, authorities);
        String tokenB = tokenSignedWith(SECRET_B, authorities);

        assertThat(jwtServiceWithSecret(SECRET_A).extractAuthorities(tokenA)).isEqualTo(authorities);
        assertThat(jwtServiceWithSecret(SECRET_B).extractAuthorities(tokenB)).isEqualTo(authorities);
    }

    @Test
    @DisplayName("getJwtRole delegates to the shared parser (one secret source, no drift)")
    void loginServiceRoleDelegatesToJwtService() {
        LoginServiceI loginService = new LoginServiceI(null, jwtServiceWithSecret(SECRET_A));
        String token = tokenSignedWith(SECRET_A, "ROLE_STUDENT,ROLE_USER");

        assertThat(loginService.getJwtRole(token)).isEqualTo("ROLE_STUDENT,ROLE_USER");
    }
}
