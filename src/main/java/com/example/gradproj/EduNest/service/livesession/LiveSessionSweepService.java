package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.livesession.policy.AttendancePolicy;
import com.example.gradproj.EduNest.service.livesession.policy.PointsAwardingPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P4-08 (EB-57 / LS-03): Background housekeeping service that automatically closes
 * lingering LIVE sessions and cancels abandoned SCHEDULED sessions.
 *
 * <p>Runs on a 5-minute fixed delay (300,000 ms) as specified by architectural directive.
 * Uses configurable grace period (default 10 min) and max duration (default 40 min, per D-29).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.livesession.auto-close.enabled", havingValue = "true", matchIfMissing = true)
public class LiveSessionSweepService {

    private final LiveSessionRepository liveSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendancePolicy attendancePolicy;
    private final PointsAwardingPolicy pointsAwardingPolicy;
    private final ZoomMeetingClient zoomMeetingClient;
    private final ZoomProperties zoomProperties;
    private final LiveSessionProperties liveSessionProperties;

    private Clock clock = Clock.systemDefaultZone();

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Scheduled(fixedDelayString = "${app.livesession.auto-close.sweep-rate-ms:300000}")
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        int closed = autoCloseExpiredLiveSessions(now);
        int cancelled = autoCancelStaleScheduledSessions(now);
        if (closed > 0 || cancelled > 0) {
            log.info("Live session auto-lifecycle sweep completed: {} closed, {} cancelled at {}", closed, cancelled, now);
        }
    }

    @Transactional
    public int autoCloseExpiredLiveSessions(LocalDateTime now) {
        int windowMinutes = liveSessionProperties.getAutoClose().getMaxDurationMinutes()
                + liveSessionProperties.getAutoClose().getGraceMinutes();
        LocalDateTime cutoff = now.minusMinutes(windowMinutes);

        List<Session> expiredLiveSessions = liveSessionRepository
                .findByStatusAndActualStartTimeBefore(SessionStatus.LIVE, cutoff);

        for (Session session : expiredLiveSessions) {
            log.info("Auto-closing live session id={} ('{}') started at {}",
                    session.getId(), session.getTitle(), session.getActualStartTime());

            session.setStatus(SessionStatus.ENDED);
            session.setActualEndTime(now);
            liveSessionRepository.save(session);

            if (zoomProperties.isEnabled() && session.getZoomMeetingId() != null) {
                try {
                    zoomMeetingClient.endMeeting(session.getZoomMeetingId());
                } catch (Exception e) {
                    log.warn("Notice on Zoom end meeting during auto-close sweep for session {}: {}",
                            session.getId(), e.getMessage());
                }
            }

            try {
                var snapshots = attendanceRepository.findBySession_Id(session.getId());
                var evaluations = attendancePolicy.evaluateAttendance(session, snapshots);
                pointsAwardingPolicy.awardPoints(session, evaluations);
            } catch (Exception e) {
                log.error("Failed to process attendance during auto-close for session {}: {}",
                        session.getId(), e.getMessage());
            }
        }

        return expiredLiveSessions.size();
    }

    @Transactional
    public int autoCancelStaleScheduledSessions(LocalDateTime now) {
        int windowMinutes = liveSessionProperties.getAutoClose().getMaxDurationMinutes()
                + liveSessionProperties.getAutoClose().getGraceMinutes();
        LocalDateTime cutoff = now.minusMinutes(windowMinutes);

        List<Session> staleScheduledSessions = liveSessionRepository
                .findByStatusAndScheduledAtBefore(SessionStatus.SCHEDULED, cutoff);

        for (Session session : staleScheduledSessions) {
            log.info("Auto-cancelling stale scheduled session id={} ('{}') scheduled at {}",
                    session.getId(), session.getTitle(), session.getScheduledAt());

            session.setStatus(SessionStatus.CANCELLED);
            liveSessionRepository.save(session);

            if (zoomProperties.isEnabled() && session.getZoomMeetingId() != null) {
                try {
                    zoomMeetingClient.deleteMeeting(session.getZoomMeetingId());
                } catch (Exception e) {
                    log.warn("Notice on Zoom delete meeting during auto-cancel sweep for session {}: {}",
                            session.getId(), e.getMessage());
                }
            }
        }

        return staleScheduledSessions.size();
    }
}
