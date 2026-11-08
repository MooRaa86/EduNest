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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * P4-08 (EB-57 / LS-03 / EB-51 D-15b): Non-transactional background housekeeping dispatcher
 * that automatically closes lingering LIVE sessions and cancels abandoned SCHEDULED sessions.
 *
 * <p>Dispatches each session to {@link LiveSessionSweepWorker} in its own individual transaction,
 * ensuring failure on one session never aborts the entire sweep batch.
 *
 * <p>Runs on a 5-minute fixed delay (300,000 ms) as specified by architectural directive.
 * Uses configurable grace period (default 10 min) and max duration (default 40 min, per D-29).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.livesession.auto-close.enabled", havingValue = "true", matchIfMissing = true)
public class LiveSessionSweepService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionSweepWorker sweepWorker;
    private final LiveSessionProperties liveSessionProperties;

    private Clock clock = Clock.systemDefaultZone();

    @Autowired
    public LiveSessionSweepService(
            LiveSessionRepository liveSessionRepository,
            LiveSessionSweepWorker sweepWorker,
            LiveSessionProperties liveSessionProperties
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.sweepWorker = sweepWorker;
        this.liveSessionProperties = liveSessionProperties;
    }

    /**
     * Backward-compatible test convenience constructor.
     */
    public LiveSessionSweepService(
            LiveSessionRepository liveSessionRepository,
            AttendanceRepository attendanceRepository,
            AttendancePolicy attendancePolicy,
            PointsAwardingPolicy pointsAwardingPolicy,
            ZoomMeetingClient zoomMeetingClient,
            ZoomProperties zoomProperties,
            LiveSessionProperties liveSessionProperties
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.liveSessionProperties = liveSessionProperties;
        this.sweepWorker = new LiveSessionSweepWorker(
                liveSessionRepository,
                null,
                null,
                attendanceRepository,
                attendancePolicy,
                pointsAwardingPolicy,
                zoomMeetingClient,
                zoomProperties,
                null
        );
    }

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Scheduled(fixedDelayString = "${app.livesession.auto-close.sweep-rate-ms:300000}")
    public void sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        int closed = autoCloseExpiredLiveSessions(now);
        int cancelled = autoCancelStaleScheduledSessions(now);
        if (closed > 0 || cancelled > 0) {
            log.info("Live session auto-lifecycle sweep completed: {} closed, {} cancelled at {}", closed, cancelled, now);
        }
    }

    public int autoCloseExpiredLiveSessions(LocalDateTime now) {
        int windowMinutes = liveSessionProperties.getAutoClose().getMaxDurationMinutes()
                + liveSessionProperties.getAutoClose().getGraceMinutes();
        LocalDateTime cutoff = now.minusMinutes(windowMinutes);

        List<Session> expiredLiveSessions = new ArrayList<>(liveSessionRepository
                .findByStatusAndActualStartTimeBefore(SessionStatus.LIVE, cutoff));
        expiredLiveSessions.sort(Comparator
                .comparing((Session s) -> s.getWeek().getMentorship().getId())
                .thenComparing(s -> s.getWeek().getId())
                .thenComparing(Session::getId));

        int closedCount = 0;
        for (Session session : expiredLiveSessions) {
            try {
                log.info("Auto-closing live session id={} ('{}') started at {}",
                        session.getId(), session.getTitle(), session.getActualStartTime());
                boolean closed = sweepWorker.closeExpiredSession(session, now);
                if (closed) {
                    closedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to auto-close session id={}: {}", session.getId(), e.getMessage(), e);
            }
        }

        return closedCount;
    }

    public int autoCancelStaleScheduledSessions(LocalDateTime now) {
        int windowMinutes = liveSessionProperties.getAutoClose().getMaxDurationMinutes()
                + liveSessionProperties.getAutoClose().getGraceMinutes();
        LocalDateTime cutoff = now.minusMinutes(windowMinutes);

        List<Session> staleScheduledSessions = new ArrayList<>(liveSessionRepository
                .findByStatusAndScheduledAtBefore(SessionStatus.SCHEDULED, cutoff));
        staleScheduledSessions.sort(Comparator
                .comparing((Session s) -> s.getWeek().getMentorship().getId())
                .thenComparing(s -> s.getWeek().getId())
                .thenComparing(Session::getId));

        int cancelledCount = 0;
        for (Session session : staleScheduledSessions) {
            try {
                log.info("Auto-cancelling stale scheduled session id={} ('{}') scheduled at {}",
                        session.getId(), session.getTitle(), session.getScheduledAt());
                boolean cancelled = sweepWorker.cancelStaleScheduledSession(session, now);
                if (cancelled) {
                    cancelledCount++;
                }
            } catch (Exception e) {
                log.error("Failed to auto-cancel session id={}: {}", session.getId(), e.getMessage(), e);
            }
        }

        return cancelledCount;
    }
}
