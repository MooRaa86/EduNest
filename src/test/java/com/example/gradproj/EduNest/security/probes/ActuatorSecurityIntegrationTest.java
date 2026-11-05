package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.config.security.JwtSecretProvider;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** EB-67: real production security chain, H2 and a controlled health indicator. */
@Import(ActuatorSecurityIntegrationTest.HealthTestConfig.class)
@TestPropertySource(properties = "management.health.mail.enabled=false")
class ActuatorSecurityIntegrationTest extends ProdChainIntegrationTestBase {
    private static final String ADMIN = "actuator-admin@example.org";
    private static final String STUDENT = "actuator-student@example.org";
    private static final String[] PROBES = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"};

    @Autowired private ApplicationContext context;
    @Autowired private AuthFixture auth;
    @Autowired private JwtSecretProvider jwtSecretProvider;
    @Autowired private AtomicBoolean testDependencyDown;

    @TestConfiguration
    static class HealthTestConfig {
        @Bean AtomicBoolean testDependencyDown() { return new AtomicBoolean(false); }
        @Bean HealthIndicator controlledHealthIndicator(AtomicBoolean down) {
            return () -> down.get() ? Health.down().withDetail("sensitive", "must-not-leak").build() : Health.up().build();
        }
    }

    @BeforeEach
    void prepare() {
        testDependencyDown.set(false);
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @AfterEach
    void resetAvailability() {
        testDependencyDown.set(false);
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void anonymousProbesAreStatusOnlyAndIgnoreInvalidJwt() throws Exception {
        String expired = Jwts.builder().subject("expired").issuedAt(new Date(1_000))
                .expiration(new Date(2_000)).signWith(jwtSecretProvider.getSecretKey()).compact();
        for (String path : PROBES) {
            for (String token : new String[]{"", "malformed-token", expired}) {
                mockMvc.perform(get(path).header("Authorization", token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("UP"))
                        .andExpect(jsonPath("$.*", hasSize(path.equals("/actuator/health") ? 2 : 1)))
                        .andExpect(jsonPath("$.components").doesNotExist())
                        .andExpect(jsonPath("$.details").doesNotExist());
            }
        }
        mockMvc.perform(get("/ctx/actuator/health").contextPath("/ctx").header("Authorization", "invalid"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.groups", org.hamcrest.Matchers.containsInAnyOrder("liveness", "readiness")));
        for (String path : PROBES) mockMvc.perform(head(path)).andExpect(status().isOk());
    }

    @Test
    void dependencyFailureAffectsOverallHealthButNotProbeGroups() throws Exception {
        testDependencyDown.set(true);
        mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must-not-leak"))));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void availabilityTransitionsProduce503WithoutDetails() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.*", hasSize(1)));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void infoRequiresAdminAndOtherManagementPathsAreDenied() throws Exception {
        auth.student(STUDENT);
        auth.admin(ADMIN);
        String student = auth.authToken(STUDENT, AuthFixture.STUDENT);
        String admin = auth.authToken(ADMIN, AuthFixture.ADMIN);
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").header("Authorization", student)).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/info").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.env").doesNotExist())
                .andExpect(jsonPath("$.configprops").doesNotExist());
        testDependencyDown.set(true);
        mockMvc.perform(get("/actuator/health").header("Authorization", admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must-not-leak"))));
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/configprops", "/actuator/heapdump", "/actuator/beans", "/actuator/mappings", "/actuator/health/db", "/actuator/health/readiness/extra"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", student)).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", admin)).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "malformed-token")).andExpect(status().isUnauthorized());
        }
        for (String path : PROBES) {
            mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(put(path).header("Authorization", admin)).andExpect(status().isForbidden());
        }
    }
}

/** The explicitly opted-in dev chain must not expose management diagnostics either. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class ActuatorDevSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext context;

    @Test
    void devChainKeepsProbeAndManagementBoundaries() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.details").doesNotExist());
            mockMvc.perform(head(path)).andExpect(status().isOk());
            mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").session(sessionWithRole("ROLE_STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/info").session(sessionWithRole("ROLE_ADMIN"))).andExpect(status().isOk());
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/health/db", "/actuator/health/readiness/extra"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).session(sessionWithRole("ROLE_ADMIN"))).andExpect(status().isForbidden());
        }
    }

    private static MockHttpSession sessionWithRole(String role) {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new TestingAuthenticationToken("test-user", null, role));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }
}
