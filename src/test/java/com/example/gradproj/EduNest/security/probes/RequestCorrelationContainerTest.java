package com.example.gradproj.EduNest.security.probes;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies real servlet ERROR dispatch and header persistence, not only MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"prod", "test"})
@TestPropertySource(properties = {"management.health.mail.enabled=false", "app.scheduling.enabled=false"})
@Import(RequestCorrelationContainerTest.Config.class)
class RequestCorrelationContainerTest {
    record Observation(DispatcherType dispatch, String id) { }
    @LocalServerPort int port;
    @Autowired ConcurrentLinkedQueue<Observation> observations;

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean ConcurrentLinkedQueue<Observation> observations() { return new ConcurrentLinkedQueue<>(); }
        @Bean FilterRegistrationBean<Filter> observeCorrelation(ConcurrentLinkedQueue<Observation> observations) {
            Filter observer = (req, resp, chain) -> {
                observations.add(new Observation(req.getDispatcherType(), MDC.get("requestId")));
                chain.doFilter(req, resp);
            };
            var registration = new FilterRegistrationBean<>(observer);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
            registration.setAsyncSupported(true);
            return registration;
        }
        @Bean ServletRegistrationBean<HttpServlet> correlationErrorServlet() {
            return new ServletRegistrationBean<>(new HttpServlet() {
                @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
                    response.sendError(500);
                }
            }, "/api/auth/eb68-container-error");
        }
        @Bean ServletRegistrationBean<HttpServlet> correlationAsyncServlet() {
            ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {
                @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
                    if (request.getDispatcherType() == DispatcherType.ASYNC) response.getWriter().write("ok");
                    else request.startAsync().dispatch();
                }
            }, "/api/auth/eb68-container-async");
            registration.setAsyncSupported(true);
            return registration;
        }
    }

    @Test
    void containerErrorRedispatchRetainsSameId() throws Exception {
        observations.clear();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/auth/eb68-container-error"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 400);
        String id = response.headers().firstValue("X-Correlation-ID").orElseThrow();
        assertNotNull(UUID.fromString(id));
        assertTrue(observations.stream().anyMatch(o -> o.dispatch() == DispatcherType.REQUEST && id.equals(o.id())));
        assertTrue(observations.stream().anyMatch(o -> o.dispatch() == DispatcherType.ERROR && id.equals(o.id())));
    }

    @Test
    void containerAsyncRedispatchRetainsSameId() throws Exception {
        observations.clear();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/auth/eb68-container-async"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String id = response.headers().firstValue("X-Correlation-ID").orElseThrow();
        assertNotNull(UUID.fromString(id));
        assertTrue(observations.stream().anyMatch(o -> o.dispatch() == DispatcherType.REQUEST && id.equals(o.id())));
        assertTrue(observations.stream().anyMatch(o -> o.dispatch() == DispatcherType.ASYNC && id.equals(o.id())));
    }
}
