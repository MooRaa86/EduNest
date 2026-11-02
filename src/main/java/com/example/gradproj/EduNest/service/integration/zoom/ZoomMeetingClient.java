package com.example.gradproj.EduNest.service.integration.zoom;

/**
 * Zoom meeting surface — the seam Zoom consumers (live-session service,
 * {@code P4-17}/EB-66) program against. Two operations cover the D-16 scope:
 * server-created meetings + details read for the attendance flows (LS-02..05).
 *
 * <p><b>Groundwork status (EB-85):</b> implemented TODAY only by
 * {@link DisabledZoomMeetingClient} — a boot-safe placeholder that fails fast
 * with an actionable message if anything calls it before credentials are
 * provisioned and the real HTTP client lands. No Zoom REST wiring exists yet
 * by design; adding it must REUSE these records verbatim (call sites freeze).</p>
 */
public interface ZoomMeetingClient {

    /** Create (schedule) a meeting through Zoom on behalf of the account-level app. */
    ZoomMeetingResult createMeeting(ZoomMeetingCreateRequest request);

    /** Read back a meeting's details (attendance/snapshot flows). */
    ZoomMeetingDetails getMeetingDetails(long zoomMeetingId);
}
