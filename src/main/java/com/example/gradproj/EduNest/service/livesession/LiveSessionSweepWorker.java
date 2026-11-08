package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.livesession.policy.AttendancePolicy;
import com.example.gradproj.EduNest.service.livesession.policy.PointsAwardingPolicy;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * EB-51 (D-15b): Dedicated Spring-managed per-session worker for live session lifecycle transitions.
 *
 * <p>Each method executes within its own transactional boundary ({@code Propagation.REQUIRED}),
 * isolating failures so an error on one session never aborts the entire sweep batch.
 *
 * <p>Acquires locks strictly in canonical hierarchy:
 * <ol>
 *   <li>L0: Mentorship row lock ({@code mentorShipRepository.findForUpdateById})</li>
 *   <li>L1: Week row lock ({@code weekRepository.findForUpdateById})</li>
 *   <li>L2: Session row lock ({@code liveSessionRepository.findForUpdateById})</li>
 * </ol>
 * Re-checks authoritative session status under lock before performing state transitions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionSweepWorker {

    private final LiveSessionRepository liveSessionRepository;
    private final MentorShipRepository mentorShipRepository;
    private final WeekRepository weekRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendancePolicy attendancePolicy;
    private final PointsAwardingPolicy pointsAwardingPolicy;
    private final ZoomMeetingClient zoomMeetingClient;
    private final ZoomProperties zoomProperties;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean closeExpiredSession(Long sessionId, LocalDateTime now) {
        if (sessionId == null) return false;
        Session candidate = (liveSessionRepository != null)
                ? liveSessionRepository.findById(sessionId).orElse(null)
                : null;
        return closeExpiredSession(candidate, now);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean closeExpiredSession(Session candidate, LocalDateTime now) {
        if (candidate == null) {
            return false;
        }

        Long sessionId = candidate.getId();
        Long mentorshipId = candidate.getWeek().getMentorship().getId();
        Long weekId = candidate.getWeek().getId();

        // Canonical locking order: L0 mentorship -> L1 week -> L2 session
        if (mentorShipRepository != null) {
            mentorShipRepository.findForUpdateById(mentorshipId)
                    .orElseThrow(() -> new globalLogicEx("Mentorship not found: " + mentorshipId));
        }
        if (weekRepository != null) {
            weekRepository.findForUpdateById(weekId)
                    .orElseThrow(() -> new globalLogicEx("Week not found: " + weekId));
        }

        Session session = null;
        if (liveSessionRepository != null) {
            session = liveSessionRepository.findForUpdateById(sessionId).orElse(null);
            if (session == null) {
                session = liveSessionRepository.findById(sessionId).orElse(candidate);
            }
        } else {
            session = candidate;
        }

        // Re-check authoritative database status under lock (bypasses 1st-level cache staleness)
        SessionStatus currentStatus = null;
        if (liveSessionRepository != null) {
            try {
                currentStatus = liveSessionRepository.findStatusById(sessionId);
            } catch (Exception ignored) {
            }
        }
        if (currentStatus == null && session != null) {
            currentStatus = session.getStatus();
        }

        if (currentStatus != SessionStatus.LIVE) {
            log.info("Session {} changed status concurrently under lock (status={}), skipping auto-close",
                    sessionId, currentStatus);
            return false;
        }

        session.setStatus(SessionStatus.ENDED);
        session.setActualEndTime(now);
        if (liveSessionRepository != null) {
            liveSessionRepository.save(session);
        }

        if (zoomProperties != null && zoomProperties.isEnabled() && session.getZoomMeetingId() != null && zoomMeetingClient != null) {
            try {
                zoomMeetingClient.endMeeting(session.getZoomMeetingId());
            } catch (Exception e) {
                log.warn("Notice on Zoom end meeting during auto-close sweep for session {}: {}",
                        session.getId(), e.getMessage());
            }
        }

        // EB-51 Blocker 3: Attendance evaluation and points awarding MUST participate in the session
        // transaction atomically. Any failure rolls back the session closure so legitimate future retries
        // are not suppressed and partial points states are never committed.
        try {
            if (attendanceRepository != null && attendancePolicy != null && pointsAwardingPolicy != null) {
                var snapshots = attendanceRepository.findBySession_Id(session.getId());
                var evaluations = attendancePolicy.evaluateAttendance(session, snapshots);
                pointsAwardingPolicy.awardPoints(session, evaluations);
            }
        } catch (RuntimeException ex) {
            session.setStatus(SessionStatus.LIVE);
            throw ex;
        }

        if (notificationService != null) {
            try {
                notificationService.sendToMentorshipStudents(
                        mentorshipId,
                        "Session Ended",
                        "Session " + session.getTitle() + " has ended.",
                        NotificationType.LIVE_SESSION
                );
            } catch (Exception e) {
                log.warn("Notice on student notification for auto-closed session {}: {}", sessionId, e.getMessage());
            }
        }

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean cancelStaleScheduledSession(Long sessionId, LocalDateTime now) {
        if (sessionId == null) return false;
        Session candidate = (liveSessionRepository != null)
                ? liveSessionRepository.findById(sessionId).orElse(null)
                : null;
        return cancelStaleScheduledSession(candidate, now);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean cancelStaleScheduledSession(Session candidate, LocalDateTime now) {
        if (candidate == null) {
            return false;
        }

        Long sessionId = candidate.getId();
        Long mentorshipId = candidate.getWeek().getMentorship().getId();
        Long weekId = candidate.getWeek().getId();

        // Canonical locking order: L0 mentorship -> L1 week -> L2 session
        if (mentorShipRepository != null) {
            mentorShipRepository.findForUpdateById(mentorshipId)
                    .orElseThrow(() -> new globalLogicEx("Mentorship not found: " + mentorshipId));
        }
        if (weekRepository != null) {
            weekRepository.findForUpdateById(weekId)
                    .orElseThrow(() -> new globalLogicEx("Week not found: " + weekId));
        }

        Session session = null;
        if (liveSessionRepository != null) {
            session = liveSessionRepository.findForUpdateById(sessionId).orElse(null);
            if (session == null) {
                session = liveSessionRepository.findById(sessionId).orElse(candidate);
            }
        } else {
            session = candidate;
        }

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            return false;
        }

        session.setStatus(SessionStatus.CANCELLED);
        if (liveSessionRepository != null) {
            liveSessionRepository.save(session);
        }

        if (zoomProperties != null && zoomProperties.isEnabled() && session.getZoomMeetingId() != null && zoomMeetingClient != null) {
            try {
                zoomMeetingClient.deleteMeeting(session.getZoomMeetingId());
            } catch (Exception e) {
                log.warn("Notice on Zoom delete meeting during auto-cancel sweep for session {}: {}",
                        session.getId(), e.getMessage());
            }
        }

        return true;
    }
}
