package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendance;
import com.example.gradproj.EduNest.entity.livesession.SessionAttendanceResult;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LiveSessionServiceImp implements LiveSessionService {

    private final WeekRepository weekRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SessionAttendanceResultRepository sessionAttendanceResultRepository;
    private final JitsiService jitsiService;
    private final TotalPointsServiceImp totalPointsService;
    private final NotificationService notificationService;
    private final SecurityService securityService;

    @Override
    @Transactional
    public SessionResponseDto createSession(CreateSessionDto createSessionDto) {

        String email = securityService.getCurrentUserEmail();

        if (!securityService.isMentorOwnWeek(createSessionDto.getWeekId(), email)) {
            throw new AccessDeniedException("You are not authorized to create session for this week");
        }


        LocalDateTime scheduledAt = LocalDateTime.of(createSessionDto.getDate(), createSessionDto.getTime());

        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new globalLogicEx("Scheduled date/time must be in the future");
        }

        Week week = weekRepository.findById(createSessionDto.getWeekId())
                .orElseThrow(() -> new globalLogicEx("Week not found"));

        Session session = Session.builder()
                .scheduledAt(scheduledAt)
                .title(createSessionDto.getTitle())
                .week(week)
                .status(SessionStatus.SCHEDULED)
                .build();

        liveSessionRepository.save(session);

        notificationService.sendToMentorshipStudents(
                week.getMentorship().getId(),
                "New Session Scheduled",
                "A new session " + session.getTitle() + " has been scheduled on " + scheduledAt,
                NotificationType.LIVE_SESSION
        );

        return mapToDto(session);
    }

    @Override
    @Transactional
    public SessionResponseDto updateSession(Long sessionId, UpdateSessionDto updateSessionDto) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to update this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (updateSessionDto.getDate() != null && updateSessionDto.getTime() != null) {
            LocalDateTime newScheduledAt = LocalDateTime.of(updateSessionDto.getDate(), updateSessionDto.getTime());

            if (newScheduledAt.isBefore(LocalDateTime.now())) {
                throw new globalLogicEx("Scheduled date/time must be in the future");
            }
            session.setScheduledAt(newScheduledAt);
        } else if (updateSessionDto.getDate() != null || updateSessionDto.getTime() != null) {
            throw new globalLogicEx("Both date and time must be provided to update scheduledAt");
        }

        if (updateSessionDto.getTitle() != null && !updateSessionDto.getTitle().isBlank()) {
            session.setTitle(updateSessionDto.getTitle());
        }

        liveSessionRepository.save(session);

        notificationService.sendToMentorshipStudents(
                session.getWeek().getMentorship().getId(),
                "Session Updated",
                "The session " + session.getTitle() + " has been updated. New time: " + session.getScheduledAt(),
                NotificationType.LIVE_SESSION
        );

        return mapToDto(session);
    }

    @Override
    @Transactional
    public SessionResponseDto getSessionById(Long sessionId) {
        String email = securityService.getCurrentUserEmail();
        boolean isMentor = securityService.isMentorOwnLiveSession(sessionId, email);
        boolean isStudent = securityService.isStudentEnrolledByLiveSessionId(email, sessionId);

        if (!isMentor && !isStudent) {
            throw new AccessDeniedException("You are not authorized to access this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));


        return mapToDto(session);
    }

    @Override
    @Transactional
    public PageResponse<DashboardSessionResponse> getAllSessions(
            Long mentorshipId,
            int page,
            int size
    ) {

        String email = securityService.getCurrentUserEmail();

        boolean isMentor =
                securityService.isMentorOwnMentorship(mentorshipId, email);

        boolean isStudent =
                securityService.isStudentEnrolledByMentorshipId(email, mentorshipId);

        if (!isMentor && !isStudent) {
            throw new AccessDeniedException(
                    "You are not authorized to access these sessions"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<DashboardSessionResponse> allSessions =
                liveSessionRepository.findAllByMentorshipId(
                        mentorshipId,
                        pageable
                );

        return PageResponse.<DashboardSessionResponse>builder()
                .content(allSessions.getContent())
                .page(allSessions.getNumber())
                .size(allSessions.getSize())
                .totalElements(allSessions.getTotalElements())
                .totalPages(allSessions.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to delete this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() == SessionStatus.LIVE) {
            throw new globalLogicEx("Session already started and cannot be deleted");
        }

        notificationService.sendToMentorshipStudents(
                session.getWeek().getMentorship().getId(),
                "Session Cancelled",
                "Session " + session.getTitle() + " has been cancelled.",
                NotificationType.LIVE_SESSION
        );

        liveSessionRepository.delete(session);
    }

    @Override
    public SessionResponseDto startLiveSession(Long sessionId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to start this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() == SessionStatus.LIVE) {
            throw new globalLogicEx("Session already started");
        }

        if (session.getStatus() == SessionStatus.ENDED) {
            throw new globalLogicEx("Session already ended");
        }

        if (session.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new globalLogicEx("Session cannot be started before scheduled time");
        }

        String meetingUrl = jitsiService.createRoomLink(sessionId);
        session.setMeetingUrl(meetingUrl);
        session.setStatus(SessionStatus.LIVE);
        session.setActualStartTime(LocalDateTime.now());

        liveSessionRepository.save(session);

        notificationService.sendToMentorshipStudents(
                session.getWeek().getMentorship().getId(),
                "Session Started",
                "Session " + session.getTitle() + " has started. Join now!",
                NotificationType.LIVE_SESSION
        );

        return mapToDto(session);
    }

    @Override
    @Transactional
    public SessionResponseDto joinSession(Long sessionId) {

        String email = securityService.getCurrentUserEmail();

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        boolean isStudent =
                securityService.isStudentEnrolledByLiveSessionId(email, sessionId);

        boolean isMentor =
                securityService.isMentorOwnLiveSession(sessionId, email);

        if (!isStudent && !isMentor) {
            throw new AccessDeniedException("You are not authorized to join this session");
        }

        if (session.getStatus() == SessionStatus.SCHEDULED) {
            throw new globalLogicEx("Session not started yet");
        }

        if (session.getStatus() == SessionStatus.ENDED) {
            throw new globalLogicEx("Session already ended");
        }

        if (session.getMeetingUrl() == null) {
            throw new globalLogicEx("Meeting link not ready");
        }

        return mapToDto(session);
    }

    @Override
    @Transactional
    public SessionResponseDto endSession(Long sessionId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to end this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() != SessionStatus.LIVE) {
            throw new globalLogicEx("Session must be live");
        }

        session.setStatus(SessionStatus.ENDED);
        session.setActualEndTime(LocalDateTime.now());
        liveSessionRepository.save(session);

        notificationService.sendToMentorshipStudents(
                session.getWeek().getMentorship().getId(),
                "Session Ended",
                "Session " + session.getTitle() + " has ended.",
                NotificationType.LIVE_SESSION
        );

        calculateAttendancePoints(session);
        return mapToDto(session);
    }

    public void recordSnapshot(Long sessionId, List<Long> studentIds) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to record attendance for this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        Long mentorshipId = session.getWeek().getMentorship().getId();

        for (Long studentId : studentIds) {
            boolean isStudentExistInThisMentorship = enrollmentRepository.existsByMentorShip_IdAndStudent_Id(mentorshipId, studentId);

            if (!isStudentExistInThisMentorship) {
                throw new globalLogicEx("Student with id " + studentId + " is not enrolled in mentorship " + mentorshipId);
            }

            Student student = studentRepository.findById(studentId).orElseThrow(() -> new globalLogicEx("Student not found"));

            SessionAttendance sessionAttendance = SessionAttendance
                    .builder()
                    .session(session)
                    .student(student)
                    .snapshotTime(LocalDateTime.now())
                    .build();

            attendanceRepository.save(sessionAttendance);
        }
    }

    @Override
    @Transactional
    public AttendanceResponse getStudentAttendanceResult(Long sessionId) {

        String email = securityService.getCurrentUserEmail();

        if (!securityService.isStudentEnrolledByLiveSessionId(email, sessionId)) {
            throw new AccessDeniedException("You are not authorized to access this attendance result");
        }

        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        SessionAttendanceResult result = sessionAttendanceResultRepository
                .findBySession_IdAndStudent_Id(sessionId, student.getId())
                .orElseThrow(() -> new globalLogicEx("Attendance not found"));

        return AttendanceResponse.builder()
                .sessionId(result.getSession().getId())
                .studentId(result.getStudent().getId())
                .status(result.isAttended() ? "Present" : "Absent")
                .build();
    }

    @Override
    @Transactional
    public List<AttendanceResponse> getAttendanceResult(Long sessionId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to view attendance for this session");
        }

        List<SessionAttendanceResult> results = sessionAttendanceResultRepository.findBySession_Id(sessionId);
        return results.stream()
                .map(r -> AttendanceResponse.builder()
                        .sessionId(r.getSession().getId())
                        .studentId(r.getStudent().getId())
                        .status(r.isAttended() ? "Present" : "Absent")
                        .build())
                .toList();
    }

    private List<AttendanceResponse> getSessionAttendance(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));


        if (session.getActualStartTime() == null) {
            throw new globalLogicEx("Session not started yet");

        }
        if (session.getActualEndTime() == null) {
            throw new globalLogicEx("Session not finished yet");
        }

        Long sessionDuration = Duration.between(session.getActualStartTime(), session.getActualEndTime()).toMinutes();

        List<SessionAttendance> allSnapshots =
                attendanceRepository.findBySession_Id(sessionId);

        Map<Student, Long> studentAttendances = new HashMap<>();
        for (SessionAttendance sessionAttendance : allSnapshots) {
            Student student = sessionAttendance.getStudent();
            studentAttendances.put(student, studentAttendances.getOrDefault(student, 0L) + 1);
        }

        long totalSnapshots = Math.max(1, sessionDuration / 5);

        List<AttendanceResponse> attendanceReport = new ArrayList<>();
        for (Map.Entry<Student, Long> studentAttendance : studentAttendances.entrySet()) {
            Student student = studentAttendance.getKey();
            Long studentSnapshots = studentAttendance.getValue();

            double attendancePercentage = (studentSnapshots * 100.0) / totalSnapshots;

            String status = attendancePercentage >= 75 ? "Present" : "Absent";
            attendanceReport.add(
                    AttendanceResponse.builder()
                            .sessionId(session.getId())
                            .studentId(student.getId())
                            .attendancePercentage(attendancePercentage)
                            .status(status)
                            .build()
            );


        }

        return attendanceReport;

    }

    private void calculateAttendancePoints(Session session) {
        List<AttendanceResponse> results = getSessionAttendance(session.getId());
        Long mentorshipId = session.getWeek().getMentorship().getId();

        Map<Long, String> attendanceMap = new HashMap<>();
        for (AttendanceResponse result : results) {
            attendanceMap.put(result.getStudentId(), result.getStatus());
        }

        List<Student> allStudents = enrollmentRepository.findStudentsByMentorshipId(mentorshipId);

        for (Student student : allStudents) {
            String status = attendanceMap.getOrDefault(student.getId(), "Absent");
            boolean attended = "Present".equals(status);

            SessionAttendanceResult attendanceResult = SessionAttendanceResult.builder()
                    .session(session)
                    .student(student)
                    .attended(attended)
                    .build();

            sessionAttendanceResultRepository.save(attendanceResult);

            int pointsEarned = 0;
            if (attended) {
                pointsEarned = 5;
                totalPointsService.applyDelta(student, session.getWeek().getMentorship(), pointsEarned);
            }

            notificationService.sendToUserByEmail(
                    student.getEmail(),
                    "Session Attendance Result",
                    "Your attendance for session " + session.getTitle() + " is: " + status +
                            (attended ? ". You earned " + pointsEarned + " points!" : "."),
                    NotificationType.LIVE_SESSION
            );
        }
    }

    private SessionResponseDto mapToDto(Session session) {
        return SessionResponseDto.builder()
                .sessionId(session.getId())
                .sessionTitle(session.getTitle())
                .sessionStartDate(session.getScheduledAt())
                .meetingUrl(session.getMeetingUrl())
                .sessionStatus(session.getStatus())
                .build();
    }

    @Override
    @Transactional
    public PageResponse<StudentUpcomingSessionResponse> getUpcomingSessionsForStudent(int page, int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<StudentUpcomingSessionResponse> upcomingSessions = liveSessionRepository
                .findUpcomingSessionsByStudentId(student.getId(), LocalDateTime.now(), pageable);

        return PageResponse.<StudentUpcomingSessionResponse>builder()
                .content(upcomingSessions.getContent())
                .page(upcomingSessions.getNumber())
                .size(upcomingSessions.getSize())
                .totalElements(upcomingSessions.getTotalElements())
                .totalPages(upcomingSessions.getTotalPages())
                .build();
    }

}