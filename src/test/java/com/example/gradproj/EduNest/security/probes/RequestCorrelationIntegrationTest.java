package com.example.gradproj.EduNest.security.probes;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.gradproj.EduNest.exception.GlobalExceptionHandler;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.rate-limit.default-limit=1", "app.rate-limit.default-window-seconds=60"})
@Import(RequestCorrelationIntegrationTest.TestEndpoints.class)
class RequestCorrelationIntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private ObjectMapper mapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {
        @Bean AsyncEndpoint correlationAsyncEndpoint() { return new AsyncEndpoint(); }
    }

    @RestController
    static class AsyncEndpoint {
        @GetMapping("/api/auth/eb68-async")
        java.util.concurrent.Callable<String> async() { return () -> "ok"; }
    }

    @Test
    void asyncRedispatchKeepsResponseId() throws Exception {
        var initial = mockMvc.perform(get("/api/auth/eb68-async"))
                .andExpect(request().asyncStarted()).andReturn();
        String id = initial.getResponse().getHeader("X-Correlation-ID");
        assertNotNull(UUID.fromString(id));
        var completed = mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andReturn();
        assertEquals(id, completed.getResponse().getHeader("X-Correlation-ID"));
        assertNull(org.slf4j.MDC.get("requestId"));
    }

    @Test
    void headersCoverSecurityRejectionsRateLimitAndActuator() throws Exception {
        auth.student("correlation-test@example.org");
        String student = auth.authToken("correlation-test@example.org", AuthFixture.STUDENT);
        String[] ids = new String[5];
        ids[0] = mockMvc.perform(get("/api/v1/contact/all-messages")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader("X-Correlation-ID");
        ids[1] = mockMvc.perform(get("/api/v1/contact/all-messages").header("Authorization", student))
                .andExpect(status().isForbidden()).andReturn().getResponse().getHeader("X-Correlation-ID");
        ids[2] = mockMvc.perform(get("/api/v1/contact/all-messages").header("Authorization", "invalid"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getHeader("X-Correlation-ID");
        mockMvc.perform(post("/login-api").header("X-Forwarded-For", "192.0.2.131"));
        ids[3] = mockMvc.perform(post("/login-api").header("X-Forwarded-For", "192.0.2.131"))
                .andExpect(status().isTooManyRequests()).andReturn().getResponse().getHeader("X-Correlation-ID");
        ids[4] = mockMvc.perform(get("/actuator/health/liveness").header("Origin", "https://example.org")
                        .header("X-Correlation-ID", "attacker-controlled"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Correlation-ID")))
                .andReturn().getResponse().getHeader("X-Correlation-ID");
        var unique = new java.util.HashSet<String>();
        for (String id : ids) { assertNotNull(UUID.fromString(id)); assertTrue(unique.add(id)); }
        assertNotEquals("attacker-controlled", ids[4]);
    }

    @Test
    void unexpected500KeepsRefIdAndLogEventHasSameRequestId() throws Exception {
        auth.student("correlation-error@example.org");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = mockMvc.perform(get("/api/v1/conversation/not-a-number/messages")
                    .header("Authorization", auth.authToken("correlation-error@example.org", AuthFixture.STUDENT)))
                    .andExpect(status().isInternalServerError()).andReturn();
            String requestId = result.getResponse().getHeader("X-Correlation-ID");
            assertNotNull(UUID.fromString(requestId));
            String refId = mapper.readTree(result.getResponse().getContentAsString()).path("refId").asText();
            assertTrue(refId.matches("[0-9a-f]{8}"));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(refId)
                    && requestId.equals(event.getMDCPropertyMap().get("requestId"))));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
