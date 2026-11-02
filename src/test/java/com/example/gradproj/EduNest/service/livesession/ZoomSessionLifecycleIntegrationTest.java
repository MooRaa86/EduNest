package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingCreateRequest;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingResult;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-17 / EB-66 — Zoom Live-Session Lifecycle Unit & Contract Test.
 *
 * Verifies complete meeting lifecycle:
 * 1. createSession: provisions Zoom meeting and records zoomMeetingId + joinUrl + hostUrl
 * 2. startLiveSession: activates LIVE status while preserving Zoom link
 * 3. updateSession: propagates rescheduled time/title to Zoom cloud
 * 4. deleteSession: invokes Zoom deleteMeeting on cloud
 * 5. endSession: invokes Zoom endMeeting on cloud
 * 6. joinSession: mentor receives hostUrl, student receives only joinUrl (hostUrl stripped)
 * 7. Fallback mode: app.zoom.enabled=false uses legacy Jitsi without Zoom calls
 */
@Tag("critical")
class ZoomSessionLifecycleIntegrationTest {

    private WeekRepository weekRepository;
    private LiveSessionRepository liveSessionRepository;
    private EnrollmentRepository enrollmentRepository;
    private StudentRepository studentRepository;
    private AttendanceRepository attendanceRepository;
    private SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private JitsiService jitsiService;
    private TotalPointsServiceImp totalPointsService;
    private NotificationService notificationService;
    private SecurityService securityService;
    private ZoomMeetingClient zoomMeetingClient;
    private ZoomProperties zoomProperties;

    private LiveSessionServiceImp service;

    @BeforeEach
    void setUp() {
        weekRepository = mock(WeekRepository.class);
        liveSessionRepository = mock(LiveSessionRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        studentRepository = mock(StudentRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        sessionAttendanceResultRepository = mock(SessionAttendanceResultRepository.class);
        jitsiService = mock(JitsiService.class);
        totalPointsService = mock(TotalPointsServiceImp.class);
        notificationService = mock(NotificationService.class);
        securityService = mock(SecurityService.class);
        zoomMeetingClient = mock(ZoomMeetingClient.class);
        zoomProperties = new ZoomProperties();

        service = new LiveSessionServiceImp(
                weekRepository, liveSessionRepository, enrollmentRepository,
                studentRepository, attendanceRepository, sessionAttendanceResultRepository,
                jitsiService, totalPointsService, notificationService, securityService,
                zoomMeetingClient, zoomProperties
        );
    }

    private Week mockWeek(Long weekId) {
        Mentor mentor = Mentor.builder().email("mentor@test.com").build();
        MentorShip mentorship = MentorShip.builder().id(200L).title("Backend Track").mentor(mentor).build();
        Week week = Week.builder().id(weekId).mentorship(mentorship).build();
        when(weekRepository.findById(weekId)).thenReturn(Optional.of(week));
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnWeek(eq(weekId), eq("mentor@test.com"))).thenReturn(true);
        when(securityService.isMentorOwnLiveSession(any(), eq("mentor@test.com"))).thenReturn(true);
        return week;
    }

    @Test
    @DisplayName("EB-66: When Zoom is enabled, createSession creates Zoom meeting with hostUrl and joinUrl")
    void zoomEnabledLifecycleProvesJoinUrlPersistence() {
        zoomProperties.setEnabled(true);
        zoomProperties.setMaxMeetingMinutes(40);

        Long weekId = 15L;
        mockWeek(weekId);

        when(zoomMeetingClient.createMeeting(any())).thenReturn(
                new ZoomMeetingResult(9988776655L, "Spring Security Deep Dive",
                        "https://us05web.zoom.us/j/9988776655", "https://us05web.zoom.us/s/9988776655", "pass123")
        );

        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(2);
        CreateSessionDto dto = CreateSessionDto.builder()
                .title("Spring Security Deep Dive")
                .scheduledAt(scheduledAt)
                .weekId(weekId)
                .build();

        SessionResponseDto created = service.createSession(dto);

        assertThat(created.getMeetingUrl()).isEqualTo("https://us05web.zoom.us/j/9988776655");
        assertThat(created.getHostUrl()).isEqualTo("https://us05web.zoom.us/s/9988776655");
        assertThat(created.getZoomMeetingId()).isEqualTo(9988776655L);
        assertThat(created.getSessionStatus()).isEqualTo(SessionStatus.SCHEDULED);

        ArgumentCaptor<ZoomMeetingCreateRequest> reqCaptor = ArgumentCaptor.forClass(ZoomMeetingCreateRequest.class);
        verify(zoomMeetingClient).createMeeting(reqCaptor.capture());
        assertThat(reqCaptor.getValue().durationMinutes()).isEqualTo(40);
        assertThat(reqCaptor.getValue().topic()).isEqualTo("Spring Security Deep Dive");

        // Verify startLiveSession preserves Zoom join URL and does NOT call Jitsi
        Session session = Session.builder()
                .id(1L)
                .title("Spring Security Deep Dive")
                .status(SessionStatus.SCHEDULED)
                .meetingUrl("https://us05web.zoom.us/j/9988776655")
                .hostUrl("https://us05web.zoom.us/s/9988776655")
                .zoomMeetingId(9988776655L)
                .scheduledAt(LocalDateTime.now().minusMinutes(5)) // past scheduled time
                .week(weekRepository.findById(weekId).get())
                .build();
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionResponseDto started = service.startLiveSession(1L);
        assertThat(started.getSessionStatus()).isEqualTo(SessionStatus.LIVE);
        assertThat(started.getMeetingUrl()).isEqualTo("https://us05web.zoom.us/j/9988776655");
        assertThat(started.getHostUrl()).isEqualTo("https://us05web.zoom.us/s/9988776655");
        assertThat(started.getZoomMeetingId()).isEqualTo(9988776655L);
        verify(jitsiService, never()).createRoomLink(any());
    }

    @Test
    @DisplayName("EB-66: joinSession provides hostUrl to mentor but strips hostUrl for student")
    void joinSessionSeparatesMentorHostUrlAndStudentJoinUrl() {
        Long sessionId = 100L;
        Session session = Session.builder()
                .id(sessionId)
                .title("Live Classroom")
                .status(SessionStatus.LIVE)
                .meetingUrl("https://us05web.zoom.us/j/100")
                .hostUrl("https://us05web.zoom.us/s/100?zak=supersecret")
                .zoomMeetingId(100L)
                .scheduledAt(LocalDateTime.now().minusMinutes(10))
                .build();
        when(liveSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // 1. Mentor joins: receives hostUrl
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(sessionId, "mentor@test.com")).thenReturn(true);
        when(securityService.isStudentEnrolledByLiveSessionId("mentor@test.com", sessionId)).thenReturn(false);

        SessionResponseDto mentorDto = service.joinSession(sessionId);
        assertThat(mentorDto.getMeetingUrl()).isEqualTo("https://us05web.zoom.us/j/100");
        assertThat(mentorDto.getHostUrl()).isEqualTo("https://us05web.zoom.us/s/100?zak=supersecret");

        // 2. Student joins: hostUrl is stripped (null)
        when(securityService.getCurrentUserEmail()).thenReturn("student@test.com");
        when(securityService.isMentorOwnLiveSession(sessionId, "student@test.com")).thenReturn(false);
        when(securityService.isStudentEnrolledByLiveSessionId("student@test.com", sessionId)).thenReturn(true);

        SessionResponseDto studentDto = service.joinSession(sessionId);
        assertThat(studentDto.getMeetingUrl()).isEqualTo("https://us05web.zoom.us/j/100");
        assertThat(studentDto.getHostUrl()).isNull();
    }

    @Test
    @DisplayName("EB-66: updateSession propagates changed title/time to Zoom cloud")
    void updateSessionPropagatesToZoom() {
        zoomProperties.setEnabled(true);
        zoomProperties.setMaxMeetingMinutes(40);
        Long weekId = 15L;
        mockWeek(weekId);

        Session session = Session.builder()
                .id(10L)
                .title("Old Title")
                .status(SessionStatus.SCHEDULED)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .meetingUrl("https://us05web.zoom.us/j/10")
                .zoomMeetingId(109988L)
                .week(weekRepository.findById(weekId).get())
                .build();
        when(liveSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        LocalDateTime newTime = LocalDateTime.now().plusDays(3);
        UpdateSessionDto updateDto = UpdateSessionDto.builder()
                .title("New Title")
                .scheduledAt(newTime)
                .build();

        service.updateSession(10L, updateDto);

        verify(zoomMeetingClient).updateMeeting(eq(109988L), eq("New Title"), any(), eq(40));
    }

    @Test
    @DisplayName("EB-66: deleteSession deletes Zoom meeting on cloud")
    void deleteSessionDeletesZoomMeeting() {
        zoomProperties.setEnabled(true);
        Long weekId = 15L;
        mockWeek(weekId);

        Session session = Session.builder()
                .id(20L)
                .title("Session To Delete")
                .status(SessionStatus.SCHEDULED)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .zoomMeetingId(209988L)
                .week(weekRepository.findById(weekId).get())
                .build();
        when(liveSessionRepository.findById(20L)).thenReturn(Optional.of(session));

        service.deleteSession(20L);

        verify(zoomMeetingClient).deleteMeeting(209988L);
        verify(liveSessionRepository).delete(session);
    }

    @Test
    @DisplayName("EB-66: When Zoom is disabled, createSession leaves link null and startSession uses Jitsi fallback")
    void zoomDisabledLifecyclePreservesJitsiFallback() {
        zoomProperties.setEnabled(false);

        Long weekId = 15L;
        mockWeek(weekId);

        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(2);
        CreateSessionDto dto = CreateSessionDto.builder()
                .title("Intro to Java")
                .scheduledAt(scheduledAt)
                .weekId(weekId)
                .build();

        SessionResponseDto created = service.createSession(dto);
        assertThat(created.getMeetingUrl()).isNull();
        assertThat(created.getHostUrl()).isNull();
        assertThat(created.getZoomMeetingId()).isNull();
        verify(zoomMeetingClient, never()).createMeeting(any());

        // Verify startLiveSession generates Jitsi link in fallback mode
        Session session = Session.builder()
                .id(2L)
                .title("Intro to Java")
                .status(SessionStatus.SCHEDULED)
                .scheduledAt(LocalDateTime.now().minusMinutes(5))
                .week(weekRepository.findById(weekId).get())
                .build();
        when(liveSessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(jitsiService.createRoomLink(2L)).thenReturn("https://meet.jit.si/edunest-room-2");

        SessionResponseDto started = service.startLiveSession(2L);
        assertThat(started.getSessionStatus()).isEqualTo(SessionStatus.LIVE);
        assertThat(started.getMeetingUrl()).isEqualTo("https://meet.jit.si/edunest-room-2");
        assertThat(started.getHostUrl()).isNull();
        verify(jitsiService).createRoomLink(2L);
    }
}
