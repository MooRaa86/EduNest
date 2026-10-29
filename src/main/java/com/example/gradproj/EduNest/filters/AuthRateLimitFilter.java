package com.example.gradproj.EduNest.filters;

import com.example.gradproj.EduNest.config.security.RateLimitProperties;
import com.example.gradproj.EduNest.exception.ApiErrorCode;
import com.example.gradproj.EduNest.exception.ServletErrorWriter;
import com.example.gradproj.EduNest.service.auth.LoginLockoutService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EB-15 / P1-08 / SEL-03 — dependency-free fixed-window throttling + lockout gate.
 *
 * Omar spec (2026-10-30): 8 requests / 1 minute per gated bucket (uniform
 * default; per-bucket overrides via properties); 5 consecutive failed logins
 * -> 5-minute account lock (LoginLockoutService); entries idle 10 minutes are
 * memory-evicted; all rejections carry the Retry-After header (seconds,
 * matching the remaining wait: window remainder for RATE_LIMITED, lock
 * remainder for ACCOUNT_LOCKED).
 *
 * Key = ip | bucket | accountEmail — the email is parsed ONCE from the JSON
 * body and replayed to the controller through {@link CachedBodyHttpRequestWrapper}
 * so the stream is never consumed twice. No email in the body -> ip-only keying.
 * Baskets cover the public/credential-bearing routes only; everything else
 * passes through untouched (authenticated routes are already JWT-gated).
 */
@RequiredArgsConstructor
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final LoginLockoutService lockout;
    private final ObjectMapper objectMapper;

    /** Ordered route classification for the public credential-bearing routes. */
    private static final Map<String, String[]> BUCKET_ROUTES = new LinkedHashMap<>();

    static {
        BUCKET_ROUTES.put("LOGIN", new String[]{"/login-api"});
        BUCKET_ROUTES.put("OTP_ISSUE", new String[]{
                "/api/v1/register/send-otp", "/api/v1/register/restore", "/forget-password"});
        BUCKET_ROUTES.put("OTP_VERIFY", new String[]{
                "/api/v1/register/verify-user", "/forget-password/verify-otp"});
        BUCKET_ROUTES.put("RESTORE_CONFIRM", new String[]{"/api/v1/register/confirm-restore"});
        BUCKET_ROUTES.put("REGISTER", new String[]{"/api/v1/register/student", "/api/v1/register/mentor"});
        BUCKET_ROUTES.put("ADMIN_REGISTER", new String[]{"/api/v1/admin/register"});
    }

    private static final String LOGIN_BUCKET = "LOGIN";
    /**
     * Double-count guard: if this filter ever runs twice for the SAME request
     * (e.g. a manual plain-servlet registration alongside the security chain),
     * the second invocation sees the marker and passes through instead of
     * consuming another budget slot.
     */
    private static final String COUNTED_ATTR = "eduNestRateLimitCounted";

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong sweepCounter = new AtomicLong();

    private static final class Window {
        long startMs;
        int count;
    }

    /* ===================== filter ===================== */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String bucket = classifyPath(request.getRequestURI());
        if (bucket == null || request.getAttribute(COUNTED_ATTR) != null) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpRequestWrapper wrapped = new CachedBodyHttpRequestWrapper(request);
        Optional<String> email = extractEmail(wrapped);
        String ip = resolveIp(request);

        // ---- 1) lockout gate (account dimension; login bucket only) ----
        if (LOGIN_BUCKET.equals(bucket) && email.isPresent()) {
            long lockSeconds = lockout.remainingLockSeconds(email.get());
            if (lockSeconds > 0) {
                reject(response, request.getRequestURI(), ApiErrorCode.ERR_ACCOUNT_LOCKED,
                        "Account temporarily locked after repeated failed logins. Please try again in a few minutes.",
                        lockSeconds);
                maybeSweep();
                return;
            }
        }

        // ---- 2) fixed-window counter (ip | bucket | account) ----
        long windowMs = props.windowFor(bucket) * 1000L;
        String key = windowKey(ip, bucket, email);
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) ->
                (existing != null && now - existing.startMs < windowMs) ? existing : new Window());

        int limit;
        synchronized (w) {
            if (now - w.startMs >= windowMs) {
                w.startMs = now;
                w.count = 0;
            }
            w.count++;
            limit = props.limitFor(bucket);
            if (w.count > limit) {
                long leftMs = (w.startMs + windowMs) - System.currentTimeMillis();
                reject(response, request.getRequestURI(), ApiErrorCode.ERR_RATE_LIMITED,
                        "Too many attempts. Please wait a moment and try again.",
                        Math.max(1, leftMs / 1000L));
                maybeSweep();
                return;
            }
        }
        maybeSweep();

        request.setAttribute(COUNTED_ATTR, Boolean.TRUE);
        chain.doFilter(wrapped, response); // controller must still see the body
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.isEnabled() || classifyPath(request.getRequestURI()) == null;
    }

    /* ===================== classification / extraction ===================== */

    private String classifyPath(String uri) {
        String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        for (Map.Entry<String, String[]> bucket : BUCKET_ROUTES.entrySet()) {
            for (String route : bucket.getValue()) {
                if (path.equals(route)) {
                    return bucket.getKey();
                }
            }
        }
        return null;
    }

    private Optional<String> extractEmail(CachedBodyHttpRequestWrapper wrapped) {
        try {
            String contentType = wrapped.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(wrapped.getInputStream());
            JsonNode email = root.get("email");
            if (email != null && !email.isNull()) {
                String value = email.asText("").trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        } catch (Exception malformedOrMissing) {
            // fall through — ip-only keying; the controller reports the malformed body
        }
        return Optional.empty();
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String candidate = (xff == null || xff.isBlank())
                ? request.getRemoteAddr()
                : xff.split(",")[0].trim();
        // keep the key sane: only characters that can appear in an address
        return candidate.replaceAll("[^0-9A-Za-z.:@\\-_]", "");
    }

    private String windowKey(String ip, String bucket, Optional<String> email) {
        return ip + "|" + bucket + "|" + (email.isPresent() ? email.get().trim().toLowerCase(Locale.ROOT) : "");
    }

    private void reject(HttpServletResponse response, String path, ApiErrorCode code,
                        String message, long retryAfterSeconds) throws IOException {
        ServletErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), code,
                message, path, Map.of("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds))));
    }

    /** TTL sweep (Omar spec: 10-minute memory cleanup), opportunistic every 256 ops. */
    private void maybeSweep() {
        if ((sweepCounter.incrementAndGet() & 0xFF) != 0) {
            return;
        }
        long deadline = System.currentTimeMillis() - props.getTtlSeconds() * 1000L;
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> next = it.next();
            if (next.getValue().startMs < deadline) {
                it.remove();
            }
        }
    }

    /* ===================== body wrapper ===================== */

    /**
     * EB-15 CRITICAL DETAIL: the filter reads the JSON body to key on the
     * account email — the raw input stream would then be CONSUMED, and the
     * controller would bind an empty body. The wrapper caches the bytes once
     * and serves a FRESH stream on every getInputStream()/getReader() call.
     */
    static final class CachedBodyHttpRequestWrapper extends HttpServletRequestWrapper {

        private static final int MAX_CACHED_BODY_BYTES = 64_000;

        private final byte[] body;

        CachedBodyHttpRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = readRawBody(request);
        }

        private static byte[] readRawBody(HttpServletRequest request) throws IOException {
            int declared = request.getContentLength();
            if (declared > MAX_CACHED_BODY_BYTES) {
                // never cache oversized bodies; stream them straight through
                return new byte[0];
            }
            return request.getInputStream().readAllBytes();
        }

        /** A fresh, fully-readable stream for each consumer (filter, Jackson, controller). */
        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // non-async, blocking servlet IO — no listener protocol used
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
