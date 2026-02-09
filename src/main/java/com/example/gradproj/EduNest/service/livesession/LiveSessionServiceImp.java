package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LiveSessionServiceImp implements LiveSessionService {

    private final mentorShipRepository mentorShipRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final JitsiService jitsiService;

    @Override
    public SessionResponseDto createSession(CreateSessionDto createSessionDto) {

        LocalDateTime scheduledAt = LocalDateTime.of(createSessionDto.getDate(), createSessionDto.getTime());

        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new globalLogicEx("Scheduled date/time must be in the future");
        }

        mentorShipE mentorShip = mentorShipRepository.findById(createSessionDto.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        Session session = Session.builder()
                .scheduledAt(scheduledAt)
                .title(createSessionDto.getTitle())
                .mentorship(mentorShip)
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

        String meetingUrl = jitsiService.createRoomLink(sessionId);
        session.setMeetingUrl(meetingUrl);
        session.setStatus(SessionStatus.LIVE);

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
        liveSessionRepository.save(session);

        return mapToDto(session);
    }


    private SessionResponseDto mapToDto(Session session) {
        return SessionResponseDto.builder()
                .sessionId(session.getSessionId())
                .sessionTitle(session.getTitle())
                .sessionStartDate(session.getScheduledAt())
                .meetingUrl(session.getMeetingUrl())
                .sessionStatus(session.getStatus())
                .build();
    }
}
