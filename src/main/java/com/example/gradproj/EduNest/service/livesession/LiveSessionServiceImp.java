package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.integrations.ZoomProperties;
import com.example.gradproj.EduNest.config.livesession.LiveSessionProperties;
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
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.livesession.SessionAttendanceResultRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingClient;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingCreateRequest;
import com.example.gradproj.EduNest.service.integration.zoom.ZoomMeetingResult;
import com.example.gradproj.EduNest.service.livesession.policy.AttendancePolicy;
import com.example.gradproj.EduNest.service.livesession.policy.PointsAwardingPolicy;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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
    private final ZoomMeetingClient zoomMeetingClient;
    private final ZoomProperties zoomProperties;
    private final AttendancePolicy attendancePolicy;
    private final PointsAwardingPolicy pointsAwardingPolicy;
    private final LiveSessionProperties liveSessionProperties;

    @Override
    @Transactional
    public SessionResponseDto createSession(CreateSessionDto createSessionDto) {

        String email = securityService.getCurrentUserEmail();

        if (!securityService.isMentorOwnWeek(createSessionDto.getWeekId(), email)) {
            throw new AccessDeniedException("You are not authorized to create session for this week");
        }

        LocalDateTime scheduledAt = createSessionDto.getScheduledAt();
        if (scheduledAt == null) {
            throw new globalLogicEx("Scheduled date/time cannot be null");
        }

        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new globalLogicEx("Scheduled date/time must be in the future");
        }

        Week week = weekRepository.findById(createSessionDto.getWeekId())
                .orElseThrow(() -> new globalLogicEx("Week not found"));

        String meetingUrl = null;
        String hostUrl = null;
        Long zoomMeetingId = null;

        if (zoomProperties.isEnabled()) {
            Instant startInstant = scheduledAt.atZone(ZoneId.systemDefault()).toInstant();
            int duration = zoomProperties.getMaxMeetingMinutes();
            ZoomMeetingCreateRequest zoomReq = new ZoomMeetingCreateRequest(
                    createSessionDto.getTitle(),
                    startInstant,
                    duration,
                    ZoneId.systemDefault().getId(),
                    null
            );
            ZoomMeetingResult zoomResult = zoomMeetingClient.createMeeting(zoomReq);
            meetingUrl = zoomResult.joinUrl();
            hostUrl = zoomResult.startUrl();
            zoomMeetingId = zoomResult.zoomMeetingId();
            log.info("Provisioned Zoom meeting {} (joinUrl: {}, hostUrl present) for session '{}'",
                    zoomMeetingId, meetingUrl, createSessionDto.getTitle());
        }

        Session session = Session.builder()
                .scheduledAt(scheduledAt)
                .title(createSessionDto.getTitle())
                .week(week)
                .status(SessionStatus.SCHEDULED)
                .meetingUrl(meetingUrl)
                .hostUrl(hostUrl)
                .zoomMeetingId(zoomMeetingId)
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

        if (session.getStatus() == SessionStatus.LIVE) {
            throw new globalLogicEx("Session already started and cannot be updated");
        }
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new globalLogicEx("Session already ended and cannot be updated");
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new globalLogicEx("Session has been cancelled and cannot be updated");
        }

        if (updateSessionDto.getScheduledAt() != null) {
            LocalDateTime newScheduledAt = updateSessionDto.getScheduledAt();

            if (newScheduledAt.isBefore(LocalDateTime.now())) {
                throw new globalLogicEx("Scheduled date/time must be in the future");
            }
            session.setScheduledAt(newScheduledAt);
        }

        if (updateSessionDto.getTitle() != null && !updateSessionDto.getTitle().isBlank()) {
            session.setTitle(updateSessionDto.getTitle());
        }

        if (zoomProperties.isEnabled() && session.getZoomMeetingId() != null) {
            Instant newStart = (updateSessionDto.getScheduledAt() != null)
                    ? session.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant()
                    : null;
            String newTitle = (updateSessionDto.getTitle() != null && !updateSessionDto.getTitle().isBlank())
                    ? updateSessionDto.getTitle()
                    : null;
            try {
                zoomMeetingClient.updateMeeting(session.getZoomMeetingId(), newTitle, newStart, zoomProperties.getMaxMeetingMinutes());
            } catch (Exception e) {
                log.warn("Failed to sync session update to Zoom meeting {}: {}", session.getZoomMeetingId(), e.getMessage());
            }
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

        SessionResponseDto dto = mapToDto(session);
        if (!isMentor) {
            dto.setHostUrl(null);
        }
        return dto;
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

        if (zoomProperties.isEnabled() && session.getZoomMeetingId() != null) {
            try {
                zoomMeetingClient.deleteMeeting(session.getZoomMeetingId());
            } catch (Exception e) {
                log.warn("Failed to delete Zoom meeting {}: {}", session.getZoomMeetingId(), e.getMessage());
            }
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
    @Transactional
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

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new globalLogicEx("Session has been cancelled");
        }

        if (session.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new globalLogicEx("Session cannot be started before scheduled time");
        }

        if (!zoomProperties.isEnabled()) {
            String meetingUrl = jitsiService.createRoomLink(sessionId);
            session.setMeetingUrl(meetingUrl);
        } else if (session.getMeetingUrl() == null) {
            throw new globalLogicEx("Zoom meeting link not initialized");
        }

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

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new globalLogicEx("Session has been cancelled");
        }

        if (session.getMeetingUrl() == null) {
            throw new globalLogicEx("Meeting link not ready");
        }

        SessionResponseDto dto = mapToDto(session);
        // Only the mentor/host is permitted to see the hostUrl (start_url with ZAK token)
        if (!isMentor) {
            dto.setHostUrl(null);
        }
        return dto;
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

        if (zoomProperties.isEnabled() && session.getZoomMeetingId() != null) {
            try {
                zoomMeetingClient.endMeeting(session.getZoomMeetingId());
            } catch (Exception e) {
                log.debug("Notice on Zoom end meeting {}: {}", session.getZoomMeetingId(), e.getMessage());
            }
        }

        notificationService.sendToMentorshipStudents(
                session.getWeek().getMentorship().getId(),
                "Session Ended",
                "Session " + session.getTitle() + " has ended.",
                NotificationType.LIVE_SESSION
        );

        calculateAttendancePoints(session);
        return mapToDto(session);
    }

    @Override
    @Transactional
    public void recordSnapshot(Long sessionId, List<Long> studentIds) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnLiveSession(sessionId, email)) {
            throw new AccessDeniedException("You are not authorized to record attendance for this session");
        }

        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() != SessionStatus.LIVE) {
            throw new globalLogicEx("Session must be live to record attendance");
        }

        Long mentorshipId = session.getWeek().getMentorship().getId();

        List<SessionAttendance> attendances = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
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
                    .snapshotTime(now)
                    .build();

            attendances.add(sessionAttendance);
        }
        attendanceRepository.saveAll(attendances);
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

        List<SessionAttendance> allSnapshots =
                attendanceRepository.findBySession_Id(sessionId);

        return attendancePolicy.evaluateAttendance(session, allSnapshots);
    }

    private void calculateAttendancePoints(Session session) {
        List<AttendanceResponse> results = getSessionAttendance(session.getId());
        pointsAwardingPolicy.awardPoints(session, results);
    }

    private SessionResponseDto mapToDto(Session session) {
        return SessionResponseDto.builder()
                .sessionId(session.getId())
                .sessionTitle(session.getTitle())
                .sessionStartDate(session.getScheduledAt())
                .meetingUrl(session.getMeetingUrl())
                .hostUrl(session.getHostUrl())
                .sessionStatus(session.getStatus())
                .zoomMeetingId(session.getZoomMeetingId())
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
