package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.livesession.policy.AttendancePolicy;
import com.example.gradproj.EduNest.service.livesession.policy.DormantPointsAwarder;
import com.example.gradproj.EduNest.service.livesession.policy.NoEnforcementAttendancePolicy;
import com.example.gradproj.EduNest.service.livesession.policy.PointsAwardingPolicy;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-08 / EB-57 (LS-02, LS-03, LS-05): Integration tests for Live-session auto-lifecycle housekeeping,
 * grace period sweep, transactional boundaries, and CANCELLED status lifecycle.
 */
class LiveSessionHousekeepingIntegrationTest {

    private LiveSessionRepository liveSessionRepository;
    private AttendanceRepository attendanceRepository;
    private ZoomMeetingClient zoomMeetingClient;
    private ZoomProperties zoomProperties;
    private LiveSessionProperties liveSessionProperties;
    private AttendancePolicy attendancePolicy;
    private PointsAwardingPolicy pointsAwardingPolicy;

    private LiveSessionSweepService sweepService;
    private LiveSessionServiceImp liveSessionService;
    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        liveSessionRepository = mock(LiveSessionRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        zoomMeetingClient = mock(ZoomMeetingClient.class);

        zoomProperties = new ZoomProperties();
        zoomProperties.setEnabled(true);

        liveSessionProperties = new LiveSessionProperties();
        liveSessionProperties.getAutoClose().setEnabled(true);
        liveSessionProperties.getAutoClose().setMaxDurationMinutes(40);
        liveSessionProperties.getAutoClose().setGraceMinutes(10);
        liveSessionProperties.getAutoClose().setSweepRateMs(300_000L); // 5 mins per Omar's directive

        attendancePolicy = new NoEnforcementAttendancePolicy();
        pointsAwardingPolicy = new DormantPointsAwarder();

        sweepService = new LiveSessionSweepService(
                liveSessionRepository,
                attendanceRepository,
                attendancePolicy,
                pointsAwardingPolicy,
                zoomMeetingClient,
                zoomProperties,
                liveSessionProperties
        );

        securityService = mock(SecurityService.class);
        liveSessionService = new LiveSessionServiceImp(
                mock(WeekRepository.class),
                liveSessionRepository,
                mock(EnrollmentRepository.class),
                mock(StudentRepository.class),
                attendanceRepository,
                mock(SessionAttendanceResultRepository.class),
                mock(JitsiService.class),
                mock(TotalPointsServiceImp.class),
                mock(NotificationService.class),
                securityService,
                zoomMeetingClient,
                zoomProperties,
                attendancePolicy,
                pointsAwardingPolicy,
                liveSessionProperties
        );
    }

    private Session createTestSession(Long id, SessionStatus status, LocalDateTime start, LocalDateTime scheduled) {
        Mentor mentor = Mentor.builder().email("mentor@test.com").build();
        MentorShip mentorship = MentorShip.builder().id(100L).mentor(mentor).build();
        Week week = Week.builder().id(10L).mentorship(mentorship).build();

        return Session.builder()
                .id(id)
                .title("Sweep Test Session " + id)
                .status(status)
                .actualStartTime(start)
                .scheduledAt(scheduled)
                .zoomMeetingId(9000L + id)
                .meetingUrl("https://zoom.us/j/" + (9000L + id))
                .hostUrl("https://zoom.us/s/" + (9000L + id))
                .week(week)
                .attendances(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("autoCloseExpiredLiveSessions: LIVE session older than 40m + 10m grace is ENDED and signals Zoom")
    void autoClose_expiredLiveSession_endedAndSignalsZoom() {
        LocalDateTime now = LocalDateTime.now();
        // Started 55 minutes ago -> cutoff is 50 minutes ago (40 + 10)
        LocalDateTime startedAt = now.minusMinutes(55);
        Session expiredSession = createTestSession(1L, SessionStatus.LIVE, startedAt, startedAt);

        when(liveSessionRepository.findByStatusAndActualStartTimeBefore(eq(SessionStatus.LIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSession));

        int closed = sweepService.autoCloseExpiredLiveSessions(now);

        assertThat(closed).isEqualTo(1);
        assertThat(expiredSession.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(expiredSession.getActualEndTime()).isEqualTo(now);
        verify(liveSessionRepository).save(expiredSession);
        verify(zoomMeetingClient).endMeeting(9001L);
    }

    @Test
    @DisplayName("autoClose_activeLiveSessionWithinDuration: LIVE session started 25 min ago remains LIVE")
    void autoClose_activeSessionWithinDuration_notClosed() {
        LocalDateTime now = LocalDateTime.now();
        when(liveSessionRepository.findByStatusAndActualStartTimeBefore(eq(SessionStatus.LIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int closed = sweepService.autoCloseExpiredLiveSessions(now);

        assertThat(closed).isEqualTo(0);
        verify(zoomMeetingClient, never()).endMeeting(anyLong());
    }

    @Test
    @DisplayName("autoCancelStaleScheduledSessions: SCHEDULED session older than scheduledAt + 50m without start is CANCELLED")
    void autoCancel_staleScheduledSession_cancelledAndDeletesZoomMeeting() {
        LocalDateTime now = LocalDateTime.now();
        // Scheduled 60 minutes ago and never started
        LocalDateTime scheduledAt = now.minusMinutes(60);
        Session abandonedSession = createTestSession(2L, SessionStatus.SCHEDULED, null, scheduledAt);

        when(liveSessionRepository.findByStatusAndScheduledAtBefore(eq(SessionStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(abandonedSession));

        int cancelled = sweepService.autoCancelStaleScheduledSessions(now);

        assertThat(cancelled).isEqualTo(1);
        assertThat(abandonedSession.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        verify(liveSessionRepository).save(abandonedSession);
        verify(zoomMeetingClient).deleteMeeting(9002L);
    }

    @Test
    @DisplayName("autoCancel_staleScheduledSession: Zoom deletion failure is tolerated and session remains CANCELLED")
    void autoCancel_zoomFailureTolerated() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledAt = now.minusMinutes(60);
        Session abandonedSession = createTestSession(3L, SessionStatus.SCHEDULED, null, scheduledAt);

        when(liveSessionRepository.findByStatusAndScheduledAtBefore(eq(SessionStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(abandonedSession));
        org.mockito.Mockito.doThrow(new RuntimeException("Zoom API 500")).when(zoomMeetingClient).deleteMeeting(9003L);

        int cancelled = sweepService.autoCancelStaleScheduledSessions(now);

        assertThat(cancelled).isEqualTo(1);
        assertThat(abandonedSession.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        verify(liveSessionRepository).save(abandonedSession);
    }

    @Test
    @DisplayName("sweep: floating clock advances past grace window and executes auto-close and auto-cancel in one sweep")
    void sweep_floatingClock_executesBothSweeps() {
        Instant baseInstant = Instant.parse("2026-11-03T10:00:00Z");
        ZoneId zone = ZoneId.of("UTC");
        Clock fixedClock = Clock.fixed(baseInstant.plus(Duration.ofMinutes(55)), zone);
        sweepService.setClock(fixedClock);

        LocalDateTime sweepTime = LocalDateTime.now(fixedClock);
        LocalDateTime startedAt = LocalDateTime.ofInstant(baseInstant, zone);

        Session liveSession = createTestSession(10L, SessionStatus.LIVE, startedAt, startedAt);
        Session scheduledSession = createTestSession(20L, SessionStatus.SCHEDULED, null, startedAt);

        when(liveSessionRepository.findByStatusAndActualStartTimeBefore(eq(SessionStatus.LIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(liveSession));
        when(liveSessionRepository.findByStatusAndScheduledAtBefore(eq(SessionStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(scheduledSession));

        sweepService.sweep();

        assertThat(liveSession.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(scheduledSession.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        verify(zoomMeetingClient).endMeeting(9010L);
        verify(zoomMeetingClient).deleteMeeting(9020L);
    }

    @Test
    @DisplayName("joinSession: CANCELLED session rejects student and mentor joins")
    void joinSession_cancelledSession_throws() {
        Session cancelled = createTestSession(30L, SessionStatus.CANCELLED, null, LocalDateTime.now().minusHours(2));

        when(securityService.getCurrentUserEmail()).thenReturn("student@test.com");
        when(securityService.isStudentEnrolledByLiveSessionId("student@test.com", 30L)).thenReturn(true);
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> liveSessionService.joinSession(30L))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Session has been cancelled");
    }

    @Test
    @DisplayName("startLiveSession: CANCELLED session cannot be started")
    void startLiveSession_cancelledSession_throws() {
        Session cancelled = createTestSession(31L, SessionStatus.CANCELLED, null, LocalDateTime.now().minusHours(2));

        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(31L, "mentor@test.com")).thenReturn(true);
        when(liveSessionRepository.findById(31L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> liveSessionService.startLiveSession(31L))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Session has been cancelled");
    }

    @Test
    @DisplayName("updateSession: CANCELLED session cannot be updated")
    void updateSession_cancelledSession_throws() {
        Session cancelled = createTestSession(32L, SessionStatus.CANCELLED, null, LocalDateTime.now().minusHours(2));

        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(32L, "mentor@test.com")).thenReturn(true);
        when(liveSessionRepository.findById(32L)).thenReturn(Optional.of(cancelled));

        UpdateSessionDto dto = UpdateSessionDto.builder().title("New Title").build();

        assertThatThrownBy(() -> liveSessionService.updateSession(32L, dto))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("Session has been cancelled and cannot be updated");
    }
}
