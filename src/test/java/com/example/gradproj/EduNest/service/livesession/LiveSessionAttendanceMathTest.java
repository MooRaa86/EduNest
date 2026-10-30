package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionAttendanceMathTest {

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

        service = new LiveSessionServiceImp(
                weekRepository, liveSessionRepository, enrollmentRepository,
                studentRepository, attendanceRepository, sessionAttendanceResultRepository,
                jitsiService, totalPointsService, notificationService, securityService);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds a LIVE-ish session with start/end times wired like the entity graph. */
    private static Session session(long id, LocalDateTime start, LocalDateTime end) {
        Mentor mentor = Mentor.builder().email("mentor@test.com").build();
        com.example.gradproj.EduNest.entity.mentorship.MentorShip mentorship =
                com.example.gradproj.EduNest.entity.mentorship.MentorShip.builder()
                        .id(100L).title("Backend Track").mentor(mentor).build();
        Week week = Week.builder().id(10L).mentorship(mentorship).build();
        return Session.builder()
                .id(id)
                .title("Java Live Session")
                .status(SessionStatus.ENDED)
                .week(week)
                .scheduledAt(start.minusHours(1))
                .actualStartTime(start)
                .actualEndTime(end)
                .attendances(new ArrayList<>())
                .build();
    }

    private static Student student(long id, String email) {
        return Student.builder().id(id).firstName("Stu").lastName(String.valueOf(id))
                .email(email).build();
    }

    /** Reflection entry to the existing private seam `getSessionAttendance`. */
    private List<AttendanceResponse> getSessionAttendance(Long sessionId) throws Exception {
        Method m = LiveSessionServiceImp.class
                .getDeclaredMethod("getSessionAttendance", Long.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AttendanceResponse> result = (List<AttendanceResponse>) m.invoke(service, sessionId);
        return result;
    }

    /** Reflection entry to the existing private seam `calculateAttendancePoints`. */
    private void calculateAttendancePoints(Session session) throws Exception {
        Method m = LiveSessionServiceImp.class
                .getDeclaredMethod("calculateAttendancePoints", Session.class);
        m.setAccessible(true);
        m.invoke(service, session);
    }

    // ------------------------------------------------------------------
    // Section A — getSessionAttendance percentage/threshold table
    //   percentage = snapshots * 100 / max(1, durationMinutes / 5)
    //   status = percentage >= 75 ? Present : Absent   (LS-02)
    // ------------------------------------------------------------------

    static List<Object[]> attendanceTable() {
        return List.of(
                new Object[]{"50minSession_8of10Snapshots_80pct", 50, 8, 80.0, "Present"},
                new Object[]{"60minSession_9of12Snapshots_boundary75", 60, 9, 75.0, "Present"},
                new Object[]{"60minSession_8of12Snapshots_66pct", 60, 8, 66.67, "Absent"},
                new Object[]{"4minSession_gridFloorsAt1_100pct", 4, 1, 100.0, "Present"},
                new Object[]{"0minSession_gridFloorsAt1_100pct", 0, 1, 100.0, "Present"}
        );
    }

    @ParameterizedTest(name = "attendance table — {0}")
    @MethodSource("attendanceTable")
    void getSessionAttendance_tableDocumentsTodayBehavior(
            String branch, long durationMinutes, long snapshots,
            double expectedPercentage, String expectedStatus) throws Exception {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(durationMinutes));
        Student student = student(2L, "stu@test.com");

        List<SessionAttendance> snapshots_ = new ArrayList<>();
        for (long i = 0; i < snapshots; i++) {
            snapshots_.add(SessionAttendance.builder()
                    .session(session).student(student)
                    .snapshotTime(base.plusSeconds(i)).build());
        }
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySession_Id(1L)).thenReturn(snapshots_);

        List<AttendanceResponse> report = getSessionAttendance(1L);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).getStudentId()).isEqualTo(2L);
        assertThat(report.get(0).getAttendancePercentage())
                .isCloseTo(expectedPercentage, within(0.01));
        assertThat(report.get(0).getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("Snapshotless student NEVER appears in the per-student report (LS-02)")
    void studentWithoutSnapshots_notInReport() throws Exception {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(60)); // grid 12
        Student attending = student(2L, "a@test.com");

        List<SessionAttendance> snapshots_ = new ArrayList<>();
        for (long i = 0; i < 6; i++) { // 6/12 = 50% -> Present? no: 50 < 75 -> Absent
            snapshots_.add(SessionAttendance.builder()
                    .session(session).student(attending)
                    .snapshotTime(base.plusSeconds(i)).build());
        }
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySession_Id(1L)).thenReturn(snapshots_);

        List<AttendanceResponse> report = getSessionAttendance(1L);

        // Only students WITH at least one snapshot are reported (6/12 = 50% -> Absent).
        assertThat(report).hasSize(1);
        assertThat(report.get(0).getAttendancePercentage()).isCloseTo(50.0, within(0.01));
        assertThat(report.get(0).getStatus()).isEqualTo("Absent");
    }

    @Test
    @DisplayName("Percentage can EXCEED 100: snapshots > duration/5 grid (LS-02)")
    void percentageCanExceed100() throws Exception {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(50)); // grid 10
        Student student = student(2L, "stu@test.com");

        List<SessionAttendance> snapshots_ = new ArrayList<>();
        for (long i = 0; i < 12; i++) { // 12/10 = 120%
            snapshots_.add(SessionAttendance.builder()
                    .session(session).student(student)
                    .snapshotTime(base.plusSeconds(i)).build());
        }
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySession_Id(1L)).thenReturn(snapshots_);

        List<AttendanceResponse> report = getSessionAttendance(1L);

        assertThat(report.get(0).getAttendancePercentage()).isCloseTo(120.0, within(0.01));
        assertThat(report.get(0).getStatus()).isEqualTo("Present");
    }

    // ------------------------------------------------------------------
    // Section B — recordSnapshot saves one row per given student
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recordSnapshot: saves one attendance row per student in the list")
    void recordSnapshot_savesOneRowPerStudent() {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(60));
        // wire week -> mentorship path for both security and enrollment checks
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(1L, "mentor@test.com")).thenReturn(true);
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(enrollmentRepository.existsByMentorShip_IdAndStudent_Id(100L, 2L)).thenReturn(true);
        when(enrollmentRepository.existsByMentorShip_IdAndStudent_Id(100L, 3L)).thenReturn(true);
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student(2L, "a@test.com")));
        when(studentRepository.findById(3L)).thenReturn(Optional.of(student(3L, "b@test.com")));

        service.recordSnapshot(1L, List.of(2L, 3L));

        verify(attendanceRepository, times(2)).save(any(SessionAttendance.class));
    }

    @Test
    @DisplayName("recordSnapshot: student not enrolled in the mentorship is rejected (workflow doc)")
    void recordSnapshot_notEnrolled_throws() {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(60));
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(1L, "mentor@test.com")).thenReturn(true);
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(enrollmentRepository.existsByMentorShip_IdAndStudent_Id(100L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.recordSnapshot(1L, List.of(7L)))
                .isInstanceOf(globalLogicEx.class)
                .hasMessageContaining("not enrolled in mentorship 100");
    }

    // ------------------------------------------------------------------
    // Section C — end-of-session hand-off: Present +5 pts, Absent 0 (LS-02)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("calculateAttendancePoints: Present earns 5 points, Absent earns nothing (LS-02)")
    void attendancePoints_presentFive_absentZero() throws Exception {
        LocalDateTime base = LocalDateTime.now();
        Session session = session(1L, base, base.plusMinutes(50)); // grid 10
        Student present = student(2L, "present@test.com");
        Student absent = student(3L, "absent@test.com");

        List<SessionAttendance> snapshots_ = new ArrayList<>();
        for (long i = 0; i < 8; i++) { // 8/10 = 80% -> Present
            snapshots_.add(SessionAttendance.builder()
                    .session(session).student(present)
                    .snapshotTime(base.plusSeconds(i)).build());
        }
        when(securityService.getCurrentUserEmail()).thenReturn("mentor@test.com");
        when(securityService.isMentorOwnLiveSession(1L, "mentor@test.com")).thenReturn(true);
        when(liveSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySession_Id(1L)).thenReturn(snapshots_);
        // enrolled roster includes a student who was NEVER snapshotted
        when(enrollmentRepository.findStudentsByMentorshipId(100L))
                .thenReturn(List.of(present, absent));

        calculateAttendancePoints(session);

        // Present student: delta +5
        verify(totalPointsService, times(1)).applyDelta(eq(present), any(), eq(5));
        // Exactly ONE delta total, and it is not for the absent student
        verify(totalPointsService, times(1)).applyDelta(any(), any(), anyInt());
        verify(totalPointsService, never()).applyDelta(eq(absent), any(), anyInt());

        // One result row per student: attended true / false
        ArgumentCaptor<SessionAttendanceResult> resultCaptor =
                ArgumentCaptor.forClass(SessionAttendanceResult.class);
        verify(sessionAttendanceResultRepository, times(2)).save(resultCaptor.capture());
        List<SessionAttendanceResult> saved = resultCaptor.getAllValues();
        Map<Long, Boolean> attendedByStudent = saved.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r.isAttended()));
        assertThat(attendedByStudent).containsEntry(2L, true).containsEntry(3L, false);

        // Notifications: winner told about points, loser told they are absent
        verify(notificationService).sendToUserByEmail(
                eq("present@test.com"), eq("Session Attendance Result"),
                contains("You earned 5 points!"), eq(NotificationType.LIVE_SESSION));
        verify(notificationService).sendToUserByEmail(
                eq("absent@test.com"), eq("Session Attendance Result"),
                contains("is: Absent"), eq(NotificationType.LIVE_SESSION));
    }
}
