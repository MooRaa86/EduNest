package com.example.gradproj.EduNest.service.integration.zoom;

import java.time.Instant;

/**
 * Zoom meeting surface — the seam Zoom consumers (live-session service,
 * {@code P4-17}/EB-66) program against.
 *
 * Supports full meeting lifecycle:
 * - createMeeting: creates/schedules meeting on Zoom cloud (capped at 40 min per D-29 Basic plan)
 * - getMeetingDetails: reads meeting status/details from Zoom cloud
 * - updateMeeting: updates meeting topic/startTime on Zoom cloud (e.g. mentor rescheduling)
 * - deleteMeeting: deletes meeting from Zoom cloud (e.g. session cancellation)
 * - endMeeting: ends live meeting on Zoom cloud (e.g. mentor ends session)
 */
public interface ZoomMeetingClient {

    /** Create (schedule) a meeting through Zoom on behalf of the account-level app. */
    ZoomMeetingResult createMeeting(ZoomMeetingCreateRequest request);

    /** Read back a meeting's details (attendance/snapshot flows). */
    ZoomMeetingDetails getMeetingDetails(long zoomMeetingId);

    /** Update a scheduled Zoom meeting's title, start time, or duration. */
    void updateMeeting(long zoomMeetingId, String topic, Instant startTime, Integer durationMinutes);

    /** Delete a Zoom meeting from Zoom cloud (e.g. on session cancellation). */
    void deleteMeeting(long zoomMeetingId);

    /** End an active live Zoom meeting on Zoom cloud (e.g. on session end). */
    void endMeeting(long zoomMeetingId);
}
