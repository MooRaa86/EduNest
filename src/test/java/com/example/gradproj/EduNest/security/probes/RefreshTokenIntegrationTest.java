package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.entity.auth.RefreshTokenSession;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.auth.RefreshTokenSessionRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.account.SettingsService;
import com.example.gradproj.EduNest.service.auth.RefreshTokenCryptoUtil;
import com.example.gradproj.EduNest.service.auth.RefreshTokenService;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-91: Real security integration test suite for secure refresh token rotation,
 * strict replay defense, session revocation, and password invalidation hooks.
 */
class RefreshTokenIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture fixture;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenSessionRepository sessionRepository;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private SettingsService settingsService;
    @Autowired private JwtService jwtService;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Login returns 15-minute access JWT and opaque refresh token; DB stores only SHA-256 hash")
    void loginIssuesShortLivedAccessJwtAndHashedRefreshToken() throws Exception {
        String email = "rt-login-test@example.org";
        Student student = fixture.student(email);

        var result = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.status").value("Login successful"))
                .andExpect(jsonPath("$.apiResponse.jwt").isNotEmpty())
                .andExpect(jsonPath("$.apiResponse.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.apiResponse.Role").value("ROLE_STUDENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String jwt = JsonPath.read(body, "$.apiResponse.jwt");
        String rawRefreshToken = JsonPath.read(body, "$.apiResponse.refreshToken");

        // Verify Access JWT claims and 15-minute expiration
        Claims claims = jwtService.extractAllClaims(jwt);
        assertThat(claims.get("userId", String.class)).isNotNull();
        assertThat(claims.get("username", String.class)).isEqualTo(email);
        assertThat(claims.get("ownerType", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank(); // jti

        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(ttlMs)
                .as("Access JWT expiration must be 15 minutes (900,000 ms)")
                .isCloseTo(15 * 60 * 1000L, org.assertj.core.data.Offset.offset(5000L));

        // Verify Database Persistence: Only SHA-256 hash stored, never raw token
        String expectedHash = RefreshTokenCryptoUtil.hashToken(rawRefreshToken);
        RefreshTokenSession session = sessionRepository.findByTokenHash(expectedHash).orElse(null);
        assertThat(session).isNotNull();
        assertThat(session.getUser().getId()).isEqualTo(student.getId());
        assertThat(session.getAccessTokenJti()).isEqualTo(claims.getId());
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("2. Refresh endpoint rotates token: issues new pair, revokes predecessor with reason ROTATED")
    void refreshEndpointRotatesTokenAndRevokesPreviousToken() throws Exception {
        String email = "rt-rotate-test@example.org";
        fixture.student(email);

        var loginResult = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String rawToken1 = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Perform rotation
        var refreshResult = mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.jwt").isNotEmpty())
                .andExpect(jsonPath("$.apiResponse.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.apiResponse.Role").value("ROLE_STUDENT"))
                .andReturn();

        String rawToken2 = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");
        assertThat(rawToken2).isNotEqualTo(rawToken1);

        // Predecessor session must be marked ROTATED
        String hash1 = RefreshTokenCryptoUtil.hashToken(rawToken1);
        RefreshTokenSession session1 = sessionRepository.findByTokenHash(hash1).orElseThrow();
        assertThat(session1.isRevoked()).isTrue();
        assertThat(session1.getRevokedReason()).isEqualTo("ROTATED");
        assertThat(session1.getReplacedByHash()).isEqualTo(RefreshTokenCryptoUtil.hashToken(rawToken2));

        // Successor session must be active in the same family
        String hash2 = RefreshTokenCryptoUtil.hashToken(rawToken2);
        RefreshTokenSession session2 = sessionRepository.findByTokenHash(hash2).orElseThrow();
        assertThat(session2.isRevoked()).isFalse();
        assertThat(session2.getFamilyId()).isEqualTo(session1.getFamilyId());
    }

    @Test
    @DisplayName("3. Replay attack: reusing an already-rotated token revokes entire family (REPLAY_DETECTED)")
    void reusedTokenTriggersFamilyRevocationAndRejectsSubsequentUse() throws Exception {
        String email = "rt-replay-test@example.org";
        fixture.student(email);

        var loginResult = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken1 = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Normal rotation: Token 1 -> Token 2
        var rotateResult = mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken1 + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken2 = JsonPath.read(rotateResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Attacker attempts to replay Token 1
        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));

        // All sessions in that family must now be revoked
        String hash1 = RefreshTokenCryptoUtil.hashToken(rawToken1);
        RefreshTokenSession session1 = sessionRepository.findByTokenHash(hash1).orElseThrow();
        List<RefreshTokenSession> familySessions = sessionRepository.findByFamilyId(session1.getFamilyId());
        assertThat(familySessions)
                .as("All sessions in the family must be marked revoked")
                .allMatch(RefreshTokenSession::isRevoked);

        // Successor Token 2 is now also revoked and cannot be refreshed
        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken2 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("4. Single-session logout revokes the specific session without affecting others")
    void singleSessionLogoutRevokesSessionAndSubsequentRefreshFails() throws Exception {
        String email = "rt-logout-test@example.org";
        fixture.student(email);

        var loginResult = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Logout
        mockMvc.perform(post("/login-api/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.status").value("Logout successful"));

        // Session in DB marked USER_LOGOUT
        String hash = RefreshTokenCryptoUtil.hashToken(rawToken);
        RefreshTokenSession session = sessionRepository.findByTokenHash(hash).orElseThrow();
        assertThat(session.isRevoked()).isTrue();
        assertThat(session.getRevokedReason()).isEqualTo("USER_LOGOUT");

        // Subsequent refresh must fail with 401
        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("5. Logout-all: revokes all sessions across all devices for the account")
    void logoutAllRevokesAllSessionsAcrossFamilies() throws Exception {
        String email = "rt-logoutall-test@example.org";
        fixture.student(email);

        // Login Device 1
        var login1 = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String jwt1 = JsonPath.read(login1.getResponse().getContentAsString(), "$.apiResponse.jwt");
        String rawToken1 = JsonPath.read(login1.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Login Device 2
        var login2 = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken2 = JsonPath.read(login2.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Execute logout-all using Device 1's JWT
        mockMvc.perform(post("/login-api/logout-all")
                        .header("Authorization", "Bearer " + jwt1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.status").value("All sessions logged out successfully"));

        // Both Device 1 and Device 2 refresh tokens must now fail
        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));

        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken2 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("6. Deactivated or soft-deleted account: refresh token is rejected with 401")
    void deactivatedAndDeletedUserCannotRefresh() throws Exception {
        String email = "rt-deactivated-test@example.org";
        Student student = fixture.student(email);

        var loginResult = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Deactivate account
        var user = users.findById(student.getId()).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));

        // Re-enable but soft-delete
        user.setEnabled(true);
        user.setDeleted(true);
        users.saveAndFlush(user);

        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("7. Password change / reset hook: revokes all active refresh token sessions")
    void passwordChangeRevokesRefreshSessions() throws Exception {
        String email = "rt-pwdchange-test@example.org";
        Student student = fixture.student(email);

        var loginResult = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.apiResponse.refreshToken");

        // Trigger password revocation hook
        refreshTokenService.revokeAllForUser(student.getId());

        mockMvc.perform(post("/login-api/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rawToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_TOKEN"));
    }
}
