package com.example.gradproj.EduNest.service.integration.zoom;

import java.time.Instant;

/**
 * Zoom meeting read-back (GET /meetings/{zoomMeetingId}) — the fields the
 * attendance/snapshot flows (LS-02..05, per D-15 constants) actually need.
 * Zoom returns {@code start_time} as ISO-8601 (UTC); the HTTP client converts
 * to {@link Instant} before returning.
 *
 * @param zoomMeetingId Zoom numeric meeting id
 * @param topic         meeting topic
 * @param status        Zoom meeting status ("waiting", "started", "ended"…)
 * @param joinUrl       participant-facing URL
 * @param startUrl      host-facing URL
 * @param plannedStart  scheduled start as reported by Zoom (may be null for
 *                      instant meetings)
 */
public record ZoomMeetingDetails(
        long zoomMeetingId,
        String topic,
        String status,
        String joinUrl,
        String startUrl,
        Instant plannedStart
) {
}
