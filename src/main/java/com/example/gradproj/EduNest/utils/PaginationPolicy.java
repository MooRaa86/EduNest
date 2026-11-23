package com.example.gradproj.EduNest.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * P4-09 / EB-58 — THE pagination cap policy.
 *
 * Client-supplied {@code page}/{@code size} request parameters are clamped at
 * every construction point instead of reaching {@code PageRequest.of} raw.
 * Contract (Project-Owner-approved, 2026-11-14):
 * <ul>
 *   <li>{@code page < 0} → clamped to {@code 0} (page exhaustion stays a
 *       200-empty contract, never a 500);</li>
 *   <li>{@code size < 1} (zero or negative) → clamped to {@code 1};</li>
 *   <li>{@code size > MAX_PAGE_SIZE} (default {@value #MAX_PAGE_SIZE}) → clamped
 *       to {@value #MAX_PAGE_SIZE} (API-02: {@code size=10000} no longer
 *       unbounded);</li>
 *   <li>endpoint-specific default sizes are untouched — normal consumers are
 *       unaffected (clamp is silent for sizes ≤ {@value #MAX_PAGE_SIZE});</li>
 *   <li><b>Documented exception:</b> notification surfaces keep the pre-existing
 *       shipped cap of {@value #NOTIFICATION_MAX_PAGE_SIZE} (EB-50 / NOTIF-05,
 *       pinned by {@code NotificationPipelineProbesTest}) — approved
 *       compatibility exception, NOT a silent change.</li>
 * </ul>
 * Precedent: {@code NotificationService.clampPageSize} (EB-50) — this policy
 * generalizes it. Cursor-style chat pagination passes {@code page = 0} and only
 * the size is clamped ({@code beforeId} semantics unchanged, WS-03).
 *
 * Malformed (non-numeric) pagination parameters are handled at the HTTP layer —
 * 400 ERR_VALIDATION via the narrowed {@code MethodArgumentTypeMismatchException}
 * handler in {@code GlobalExceptionHandler} (exact pagination parameter-name set).
 */
public final class PaginationPolicy {

    /** Global maximum page size for client-supplied requests (P4-09). */
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * Compatibility exception: notification surfaces shipped with a cap of 100
     * (EB-50 / NOTIF-05, QA-pinned). Do not silently lower to MAX_PAGE_SIZE.
     */
    public static final int NOTIFICATION_MAX_PAGE_SIZE = 100;

    /** Minimum page size — zero/negative sizes clamp up to this. */
    public static final int MIN_PAGE_SIZE = 1;

    private PaginationPolicy() {
    }

    /**
     * Clamped {@code PageRequest.of(page, size)} equivalent (no sort).
     * Replace raw {@code PageRequest.of(page, size)} calls fed by request
     * parameters with this.
     */
    public static Pageable ofClamped(int page, int size) {
        return PageRequest.of(clampPage(page), clampSize(size));
    }

    /**
     * Clamped {@code PageRequest.of(page, size, sort)} equivalent — preserves
     * the supplied sort argument.
     */
    public static Pageable ofClamped(int page, int size, org.springframework.data.domain.Sort sort) {
        return PageRequest.of(clampPage(page), clampSize(size), sort);
    }

    /** Size-only clamp for cursor-style pagination ({@code page} is fixed at 0) and top-N limits. */
    public static int clampSize(int size) {
        return Math.max(MIN_PAGE_SIZE, Math.min(size, MAX_PAGE_SIZE));
    }

    /** Size-only clamp with an explicit compatibility cap (notifications → 100). */
    public static int clampSize(int size, int cap) {
        return Math.max(MIN_PAGE_SIZE, Math.min(size, cap));
    }

    /** Negative page indexes clamp to the first page. */
    public static int clampPage(int page) {
        return Math.max(0, page);
    }
}
