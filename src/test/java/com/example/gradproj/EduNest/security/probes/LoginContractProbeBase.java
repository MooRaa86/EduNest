package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class LoginContractProbeBase extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture fixture;
    @Autowired private ObjectMapper mapper;
    @Autowired private AuthenticationManager authenticationManager;

    abstract boolean bodyTokenExpected();

    @Test
    void loginReturnsUsableHeaderAndConfiguredBody() throws Exception {
        String email = "eb11-contract-" + bodyTokenExpected() + "@example.org";
        fixture.student(email);
        var response = mockMvc.perform(post("/login-api")
                        .header("Origin", "https://example.org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", "probe-password"))))
                .andExpect(status().isOk()).andReturn().getResponse();

        String token = response.getHeader("Authorization");
        assertThat(token).isNotBlank();
        assertThat(response.getHeader("Access-Control-Expose-Headers")).contains("Authorization");
        JsonNode body = mapper.readTree(response.getContentAsString()).path("apiResponse");
        assertThat(body.path("status").asText()).isEqualTo("Login successful");
        assertThat(body.path("Role").asText()).contains("STUDENT");
        if (bodyTokenExpected()) {
            assertThat(body.path("jwt").asText()).isEqualTo(token);
        } else {
            assertThat(body.has("jwt")).isFalse();
            assertThat(response.getContentAsString()).doesNotContain(token);
        }
        mockMvc.perform(get("/student/profile").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void authenticationManagerErasesReturnedCredentials() {
        String email = "eb11-credentials-" + bodyTokenExpected() + "@example.org";
        fixture.student(email);
        var result = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "probe-password"));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getName()).isEqualTo(email);
        assertThat(result.getAuthorities()).extracting("authority").contains("ROLE_STUDENT");
    }
}
