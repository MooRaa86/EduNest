package com.example.gradproj.EduNest.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Server-generated HTTP correlation, shared by redispatches via a request attribute. */
public final class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "requestId";
    private static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".id";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override
    protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
        correlate(request, response, chain);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        correlate(request, response, chain);
    }

    private void correlate(HttpServletRequest request, HttpServletResponse response,
                           FilterChain chain) throws ServletException, IOException {
        String id = (String) request.getAttribute(ATTRIBUTE);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, id);
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id);
        var guardedResponse = new CorrelationResponse(response, id);
        try {
            guardedResponse.setHeader(HEADER, id);
            chain.doFilter(request, guardedResponse);
        } finally {
            try {
                if (!guardedResponse.isCommitted()) guardedResponse.setHeader(HEADER, id);
            } finally {
                if (previous == null) MDC.remove(MDC_KEY); else MDC.put(MDC_KEY, previous);
            }
        }
    }

    private static final class CorrelationResponse extends HttpServletResponseWrapper {
        private final String id;
        private CorrelationResponse(HttpServletResponse response, String id) { super(response); this.id = id; }
        @Override public void setHeader(String name, String value) {
            super.setHeader(name, HEADER.equalsIgnoreCase(name) ? id : value);
        }
        @Override public void addHeader(String name, String value) {
            if (HEADER.equalsIgnoreCase(name)) super.setHeader(HEADER, id); else super.addHeader(name, value);
        }
        @Override public void reset() { super.reset(); super.setHeader(HEADER, id); }
    }
}
