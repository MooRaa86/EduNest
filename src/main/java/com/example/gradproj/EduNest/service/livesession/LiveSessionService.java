package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

public interface LiveSessionService {

    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto createSession(CreateSessionDto session);
    @PreAuthorize("hasRole('MENTOR')")
    SessionResponseDto updateSession(Long sessionId, UpdateSessionDto updateSessionDto);
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
    List<AttendanceResponse> getSessionAttendance(Long sessionId);

}
