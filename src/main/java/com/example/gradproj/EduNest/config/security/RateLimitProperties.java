package com.example.gradproj.EduNest.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * EB-15 / P1-08 — throttling + lockout configuration. Constraint D-27-safe:
 * no library, no Docker — fixed-window counters + in-memory TTL.
 *
 * Defaults approved by Omar (2026-10-30):
 *   rate limit   : 8 requests per 1 minute  (uniform across all gated buckets)
 *   lockout      : 5 consecutive failures -> 5 minutes wait
 *   entry TTL    : 10 minutes memory cleanup
 *   master switch: app.rate-limit.enabled (false in the test profile — the
 *                  prod-chain probes opt back in via @TestPropertySource)
 *
 * Buckets (keyed fast in filters {@code AuthRateLimitFilter}): LOGIN,
 * OTP_ISSUE, OTP_VERIFY, RESTORE_CONFIRM, REGISTER, ADMIN_REGISTER.
 * Per-bucket overrides are possible via properties (e.g. to re-tighten the
 * OTP endpoints without a code change):
 *   app.rate-limit.buckets.OTP_ISSUE.limit=3
 *   app.rate-limit.buckets.OTP_ISSUE.window-seconds=600
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    /** Master switch — false disables ALL throttling/lockout behavior. */
    private boolean enabled = true;

    /** Memory cleanup (Omar spec): entries idle longer than this are evicted. */
    private long ttlSeconds = 600;

    /** Uniform default (Omar spec): 8 requests / 60 seconds. */
    private int defaultLimit = 8;
    private int defaultWindowSeconds = 60;

    /** Optional per-bucket overrides of the uniform default. */
    private Map<String, Bucket> buckets = new HashMap<>();

    private Lockout lockout = new Lockout();

    public int limitFor(String bucket) {
        Bucket b = buckets.get(bucket);
        return b != null ? b.getLimit() : defaultLimit;
    }

    public int windowFor(String bucket) {
        Bucket b = buckets.get(bucket);
        return b != null ? b.getWindowSeconds() : defaultWindowSeconds;
    }

    @Getter
    @Setter
    public static class Bucket {
        private int limit = 8;
        private int windowSeconds = 60;
    }

    /** Omar spec: 5 consecutive failed logins -> 5 minutes locked. */
    @Getter
    @Setter
    public static class Lockout {
        private int threshold = 5;
        private int waitSeconds = 300;
    }
}
