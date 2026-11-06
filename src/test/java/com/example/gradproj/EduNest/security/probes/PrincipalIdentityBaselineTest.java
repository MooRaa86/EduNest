package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import com.example.gradproj.EduNest.service.jwt.JwtService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Characterizes current identity behavior; these are not security endorsements. */
class PrincipalIdentityBaselineTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture fixture;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwtService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void identityEqualityUsesTypeAndIdRatherThanEmail() {
        var original = new PrincipalIdentity(PrincipalIdentity.Type.USER, 1, "before@example.org");
        var renamed = new PrincipalIdentity(PrincipalIdentity.Type.USER, 1, "after@example.org");
        var admin = new PrincipalIdentity(PrincipalIdentity.Type.ADMIN, 1, "before@example.org");
        assertThat(original).isEqualTo(renamed).hasSameHashCodeAs(renamed).isNotEqualTo(admin);
        assertThat(original.getName()).isEqualTo("before@example.org");
        assertThat(new PrincipalIdentity(PrincipalIdentity.Type.USER, 0, "zero@example.org").id()).isZero();
        assertThat(new PrincipalIdentity(PrincipalIdentity.Type.USER, -1, "negative@example.org").id()).isEqualTo(-1);
    }

    @Test
    void administratorLoginPreservesHttpRoleAndHeader() throws Exception {
        String email = "identity-baseline-admin@example.org";
        fixture.admin(email);

        var response = mockMvc.perform(post("/login-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.Role").value("ROLE_ADMIN"))
                .andReturn().getResponse();
        assertThat(response.getHeader("Authorization")).isNotBlank();
    }

    @Test
    void collidingAccountsResolveToUserAndDoNotFallBackToAdminWhenUserDisabled() {
        String email = "identity-baseline-overlap@example.org";
        var student = fixture.student(email);
        fixture.admin(email);

        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "probe-password"));
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
        assertThat(auth.getName()).isEqualTo(email);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getPrincipal()).isEqualTo(new PrincipalIdentity(PrincipalIdentity.Type.USER, student.getId(), email));

        var user = users.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "probe-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @Transactional
    void administratorAuthenticationCarriesActualAdminIdWithoutPassword() {
        String email = "identity-typed-admin@example.org";
        var admin = fixture.admin(email);
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "probe-password"));

        assertThat(auth.getPrincipal()).isEqualTo(new PrincipalIdentity(PrincipalIdentity.Type.ADMIN, admin.getId(), email));
        assertThat(auth.getName()).isEqualTo(email);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(auth.getPrincipal().toString()).doesNotContain("probe-password", admin.getPassword());
    }

    @Test
    void legacyJwtForDisabledUserCurrentlyPassesWhenAdminSharesEmail() {
        // TEMPORARY characterization of unsafe legacy behavior, not a desired contract.
        // When JWT v2 and its legacy policy are approved, replace this acceptance
        // assertion with a rejection test for the disabled/ambiguous identity.
        String email = "identity-baseline-legacy@example.org";
        fixture.student(email);
        fixture.admin(email);
        String token = fixture.authToken(email, AuthFixture.STUDENT);
        var user = users.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);

        jwtService.validateToken(token);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo(email);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
    }
}
