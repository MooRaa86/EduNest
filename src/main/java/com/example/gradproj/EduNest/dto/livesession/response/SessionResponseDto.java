package com.example.gradproj.EduNest.dto.livesession.response;

import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter @Getter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class SessionResponseDto {

    private UUID sessionId;
    private String sessionTitle;
    private LocalDateTime sessionStartDate;
    private SessionStatus sessionStatus;
    private String meetingUrl;
    private String hostUrl;
    private Long zoomMeetingId;

    public static class SessionResponseDtoBuilder {
        public SessionResponseDtoBuilder sessionId(Long id) {
            this.sessionId = id != null ? new UUID(0L, id) : null;
            return this;
        }

        public SessionResponseDtoBuilder sessionId(UUID id) {
            this.sessionId = id;
            return this;
        }
    }
}
