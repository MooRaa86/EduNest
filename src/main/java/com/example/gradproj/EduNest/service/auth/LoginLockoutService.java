package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.config.security.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EB-15 / P1-08 / SEL-03 — per-account consecutive-failed-login lockout.
 *
 * Omar spec: 5 consecutive failures -> 5-minute lock; entries idle 10 minutes
 * are memory-evicted (TTL sweep). Resets on successful login (the provider
 * calls {@link #reset} when credentials match).
 *
 * In-memory + per-JVM is the documented trade-off (restarts clear counters;
 * no Redis/Docker per D-27; upgrade path = P5-03).
 */
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

    private final RateLimitProperties props;

    private static final class Entry {
        long count;
        long lastActivityMs;
        long lockedUntilMs; // 0 = not locked
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong sweepCounter = new AtomicLong();

    /** Records one failed login; returns the lock-remaining seconds RIGHT NOW (0 = not locked). */
    public long recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String key = normalize(email);
        lockEntry(key);
        maybeSweep();
        return remainingLockSecondsFromKey(key, System.currentTimeMillis());
    }

    /** Successful login — clears the failure streak. */
    public void reset(String email) {
        if (email != null && !email.isBlank()) {
            entries.remove(normalize(email));
        }
    }

    /** Seconds the account is still locked (0 = not locked). */
    public long remainingLockSeconds(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        return remainingLockSecondsFromKey(normalize(email), System.currentTimeMillis());
    }

    private void lockEntry(String key) {
        long now = System.currentTimeMillis();
        entries.compute(key, (k, existing) -> {
            Entry entry = (existing != null && now - existing.lastActivityMs <= props.getTtlSeconds() * 1000L)
                    ? existing
                    : new Entry();
            // an expired lock ends the streak at expiry time
            if (entry.lockedUntilMs != 0 && now >= entry.lockedUntilMs) {
                entry.lockedUntilMs = 0;
                entry.count = 0;
            }
            entry.count++;
            entry.lastActivityMs = now;
            if (entry.count >= props.getLockout().getThreshold()) {
                entry.lockedUntilMs = now + props.getLockout().getWaitSeconds() * 1000L;
            }
            return entry;
        });
    }

    private long remainingLockSecondsFromKey(String key, long now) {
        Entry e = entries.get(key);
        if (e == null) {
            return 0;
        }
        if (e.lockedUntilMs != 0) {
            if (now < e.lockedUntilMs) {
                return Math.max(1, (e.lockedUntilMs - now) / 1000L);
            }
            // lazy expiry
            entries.remove(key, e);
        }
        return 0;
    }

    /**
     * TTL sweep (Omar spec: 10-minute memory cleanup) — runs opportunistically
     * every 256 operations: a single pass over the map, evicting entries whose
     * last activity is older than the TTL.
     */
    private void maybeSweep() {
        if ((sweepCounter.incrementAndGet() & 0xFF) != 0) {
            return;
        }
        long deadline = System.currentTimeMillis() - props.getTtlSeconds() * 1000L;
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> next = it.next();
            if (next.getValue().lastActivityMs < deadline) {
                it.remove();
            }
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
