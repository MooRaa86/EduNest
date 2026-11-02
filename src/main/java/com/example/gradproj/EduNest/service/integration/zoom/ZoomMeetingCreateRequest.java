package com.example.gradproj.EduNest.service.integration.zoom;

import java.time.Instant;

/**
 * Zoom meeting create payload — the internal (backend-side) description of a
 * live session meeting. Mapping to the Zoom REST schema is the concern of the
 * HTTP client (lands with [ADD-05]/EB-85 wiring + P4-17/EB-66 consumption);
 * this record fixes the contract Zoom consumers in the codebase will program
 * against so neither placeholder nor client changes any call site.
 *
 * @param topic           meeting title (Zoom "topic")
 * @param startTime       planned start (ISO instant; client formats per Zoom's
 *                        {@code start_time} field)
 * @param durationMinutes planned duration in minutes (Zoom "duration")
 * @param timezone        IANA timezone the start_time is authored in (Zoom
 *                        "timezone"; Zoom stores/returns UTC ISO-8601)
 * @param passcode        meeting passcode — attendance/auth narrowing follows
 *                        the D-16 outcome (Zoom-native require-auth/passcode)
 *                        instead of hand-distributed public URLs (LS-01)
 */
public record ZoomMeetingCreateRequest(
        String topic,
        Instant startTime,
        int durationMinutes,
        String timezone,
        String passcode
) {

    /** Call-site contract enforcement: duration must be positive. */
    public ZoomMeetingCreateRequest {
        if (durationMinutes <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
    }
}
