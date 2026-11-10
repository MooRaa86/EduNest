package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

public interface LiveSessionService {

    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto createSession(CreateSessionDto session);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto updateSession(Long sessionId, UpdateSessionDto updateSessionDto);

    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto updateSession(UUID sessionId, UpdateSessionDto updateSessionDto);
    SessionResponseDto getSessionById(UUID id);
    @PreAuthorize("hasRole('MENTOR')")
    void deleteSession(UUID id);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto startLiveSession(UUID sessionId);
    SessionResponseDto joinSession(UUID sessionId);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto endSession(UUID sessionId);
    @PreAuthorize("hasRole('MENTOR')")
    void recordSnapshot(UUID sessionId, List<UUID> studentIds);
    @PreAuthorize("hasRole('MENTOR')")
    List<AttendanceResponse> getAttendanceResult(UUID sessionId);
    AttendanceResponse getStudentAttendanceResult(UUID sessionId);
    PageResponse<DashboardSessionResponse> getAllSessions(UUID mentorshipId, int page, int size);

    SessionResponseDto getSessionById(Long id);
    @PreAuthorize("hasRole('MENTOR')")
    void deleteSession(Long id);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto startLiveSession(Long sessionId);
    SessionResponseDto joinSession(Long sessionId);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto endSession(Long sessionId);
    @PreAuthorize("hasRole('MENTOR')")
    void recordSnapshot(Long sessionId, List<Long> studentIds);
    @PreAuthorize("hasRole('MENTOR')")
    List<AttendanceResponse> getAttendanceResult(Long sessionId);
    AttendanceResponse getStudentAttendanceResult(Long sessionId);
    PageResponse<DashboardSessionResponse> getAllSessions(Long mentorshipId, int page, int size);
    @PreAuthorize("hasRole('STUDENT')")
    PageResponse<StudentUpcomingSessionResponse> getUpcomingSessionsForStudent(int page, int size);

}
