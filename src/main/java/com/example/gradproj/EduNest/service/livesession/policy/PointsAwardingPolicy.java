package com.example.gradproj.EduNest.service.livesession.policy;

import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;

import java.util.List;

/**
 * Strategy interface for gamification points distribution at live-session end (D-15).
 */
public interface PointsAwardingPolicy {

    /**
     * Awards points, records results, and sends notifications to enrolled students.
     *
     * @param session the ended live session
     * @param results evaluated attendance results
     */
    void awardPoints(Session session, List<AttendanceResponse> results);
}
