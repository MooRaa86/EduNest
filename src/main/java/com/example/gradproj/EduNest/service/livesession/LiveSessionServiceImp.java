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
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.AttendanceRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
    private final JitsiService jitsiService;
    private final TotalPointsServiceImp totalPointsService;

    @Override
    public SessionResponseDto createSession(CreateSessionDto createSessionDto) {

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

        return mapToDto(session);
    }


    @Override
    public SessionResponseDto updateSession(Long sessionId, UpdateSessionDto updateSessionDto) {
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

        return mapToDto(session);
    }

    @Override
    public SessionResponseDto getSessionById(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));


        return mapToDto(session);
    }

    @Override
    public PageResponse<DashboardSessionResponse> getAllSessions(Long mentorshipId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<DashboardSessionResponse> allSessions =
                liveSessionRepository.findAllByMentorshipId(mentorshipId, pageable);

        return PageResponse.<DashboardSessionResponse>builder()
                .content(allSessions.getContent())
                .page(allSessions.getNumber())
                .size(allSessions.getSize())
                .totalElements(allSessions.getTotalElements())
                .totalPages(allSessions.getTotalPages())
                .build();
    }

    @Override
    public void deleteSession(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() == SessionStatus.LIVE) {
            throw new globalLogicEx("Session already started and cannot be deleted");
        }
        liveSessionRepository.delete(session);
    }

    @Override
    public SessionResponseDto startLiveSession(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() == SessionStatus.LIVE) {
            throw new globalLogicEx("Session already started");
        }

        if (session.getStatus() == SessionStatus.ENDED) {
            throw new globalLogicEx("Session already ended");
        }

        String meetingUrl = jitsiService.createRoomLink(sessionId);
        session.setMeetingUrl(meetingUrl);
        session.setStatus(SessionStatus.LIVE);
        session.setActualStartTime(LocalDateTime.now());

        liveSessionRepository.save(session);

        return mapToDto(session);
    }

    @Override
    public SessionResponseDto joinSession(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

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
    public SessionResponseDto endSession(Long sessionId) {
        Session session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new globalLogicEx("Session not found"));

        if (session.getStatus() != SessionStatus.LIVE) {
            throw new globalLogicEx("Session must be live");
        }

        session.setStatus(SessionStatus.ENDED);
        session.setActualEndTime(LocalDateTime.now());
        liveSessionRepository.save(session);

        calculateAttendancePoints(session);
        return mapToDto(session);
    }

    public void recordSnapshot(Long sessionId, List<Long> studentIds) {
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

    public List<AttendanceResponse> getSessionAttendance(Long sessionId) {
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
        List<AttendanceResponse> results =
                getSessionAttendance(session.getId());

        for (AttendanceResponse result : results) {
            if ("Present".equals(result.getStatus())) {

                Student student = studentRepository.findById(result.getStudentId())
                        .orElseThrow(() -> new globalLogicEx("Student not found"));

                totalPointsService.applyDelta(
                        student,
                        session.getWeek().getMentorship(),
                        5
                );
            }
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