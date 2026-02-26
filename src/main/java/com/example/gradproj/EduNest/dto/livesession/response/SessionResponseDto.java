package com.example.gradproj.EduNest.dto.livesession.response;

import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Setter @Getter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class SessionResponseDto {

    private Long sessionId;
    private String sessionTitle;
    private LocalDateTime sessionStartDate;
    private SessionStatus sessionStatus;
    private String meetingUrl;

}
