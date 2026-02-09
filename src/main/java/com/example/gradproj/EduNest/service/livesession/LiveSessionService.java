package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;

public interface LiveSessionService {

    SessionResponseDto createSession(CreateSessionDto session);
    SessionResponseDto updateSession(Long sessionId, UpdateSessionDto updateSessionDto);
    SessionResponseDto getSessionById(Long id);
    void deleteSession(Long id);
    SessionResponseDto startLiveSession(Long sessionId);
    SessionResponseDto joinSession(Long sessionId);
    SessionResponseDto endSession(Long sessionId);

}
