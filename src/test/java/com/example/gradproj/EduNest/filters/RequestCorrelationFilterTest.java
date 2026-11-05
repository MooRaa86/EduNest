package com.example.gradproj.EduNest.filters;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach void clean() { MDC.clear(); }

    @Test
    void ignoresAllInboundIdsAndRestoresPriorMdcOnFailure() throws Exception {
        for (String input : List.of("", "bad", "x".repeat(5000), "evil\r\ninjected: true", UUID.randomUUID().toString())) {
            var request = new MockHttpServletRequest();
            request.addHeader(RequestCorrelationFilter.HEADER, input);
            request.addHeader(RequestCorrelationFilter.HEADER, "duplicate");
            var response = new MockHttpServletResponse();
            MDC.put("requestId", "previous");
            assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, (req, res) -> {
                String id = MDC.get("requestId");
                assertNotNull(UUID.fromString(id));
                assertNotEquals(input, id);
                assertEquals(id, response.getHeader(RequestCorrelationFilter.HEADER));
                throw new IllegalStateException("controlled");
            }));
            assertEquals("previous", MDC.get("requestId"));
        }
    }

    @Test
    void asyncAndErrorRedispatchReuseRequestIdAndCleanThread() throws Exception {
        var request = new MockHttpServletRequest();
        var first = new MockHttpServletResponse();
        filter.doFilter(request, first, new MockFilterChain());
        String id = first.getHeader(RequestCorrelationFilter.HEADER);
        assertNull(MDC.get("requestId"));
        for (DispatcherType type : List.of(DispatcherType.ASYNC, DispatcherType.ERROR)) {
            request.setDispatcherType(type);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> assertEquals(id, MDC.get("requestId")));
            assertEquals(id, response.getHeader(RequestCorrelationFilter.HEADER));
            assertNull(MDC.get("requestId"));
        }
    }

    @Test
    void responseResetAndDownstreamHeaderChangesCannotReplaceId() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            var wrapped = (jakarta.servlet.http.HttpServletResponse) res;
            String id = MDC.get("requestId");
            wrapped.reset();
            wrapped.setHeader("x-correlation-id", "forged");
            wrapped.addHeader("X-Correlation-ID", "second");
            assertEquals(id, wrapped.getHeader("X-Correlation-ID"));
            assertEquals(1, wrapped.getHeaders("X-Correlation-ID").size());
            wrapped.flushBuffer();
        });
        assertNotNull(UUID.fromString(response.getHeader("X-Correlation-ID")));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void headerWriteFailureStillRestoresMdc() {
        var response = new jakarta.servlet.http.HttpServletResponseWrapper(new MockHttpServletResponse()) {
            @Override public void setHeader(String name, String value) { throw new IllegalStateException("controlled"); }
        };
        MDC.put("requestId", "previous");
        assertThrows(IllegalStateException.class, () -> filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain()));
        assertEquals("previous", MDC.get("requestId"));
    }

    @Test
    void concurrentRequestsAndReusedThreadStayIsolated() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = java.util.stream.IntStream.range(0, 20).mapToObj(i -> (java.util.concurrent.Callable<String>) () -> {
                assertNull(MDC.get("requestId"));
                var request = new MockHttpServletRequest();
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (req, res) -> {
                    assertEquals(response.getHeader(RequestCorrelationFilter.HEADER), MDC.get("requestId"));
                    Thread.yield();
                });
                assertNull(MDC.get("requestId"));
                return response.getHeader(RequestCorrelationFilter.HEADER);
            }).toList();
            var results = executor.invokeAll(calls, 10, TimeUnit.SECONDS);
            var ids = new java.util.HashSet<String>();
            for (var result : results) assertTrue(ids.add(result.get()));
            assertEquals(20, ids.size());
        }
    }
}
