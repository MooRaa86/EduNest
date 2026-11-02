package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;

import java.util.List;

/**
 * Strategy interface for live-session attendance evaluation (D-15).
 */
public interface AttendancePolicy {

    /**
     * Evaluates attendance records/snapshots for a session.
     *
     * @param session the ended live session
     * @param snapshots snapshot records taken during the session
     * @return list of attendance evaluations for each participating student
     */
    List<AttendanceResponse> evaluateAttendance(Session session, List<SessionAttendance> snapshots);
}
