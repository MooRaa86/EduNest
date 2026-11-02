package com.example.gradproj.EduNest.service.integration.zoom;

/**
 * Result of a successful Zoom meeting creation (subset of the Zoom "meeting
 * object" this backend actually consumes). Returned by
 * {@link ZoomMeetingClient#createMeeting} and persisted by the live-session
 * service (P4-17/EB-66) on the {@code} live-session entity's zoom id column.
 *
 * @param zoomMeetingId Zoom numeric meeting id (used for GET /details + attendance)
 * @param topic         canonical topic as Zoom stored it
 * @param joinUrl       participant-facing URL ("join_url") for the FE deep link
 * @param startUrl      host-facing URL ("start_url") for the mentor/owner
 * @param passcode      passcode Zoom generated/accepted (echoed back for the host
 *                      record keeping; never broadcast to non-hosts)
 */
public record ZoomMeetingResult(
        long zoomMeetingId,
        String topic,
        String joinUrl,
        String startUrl,
        String passcode
) {
}
