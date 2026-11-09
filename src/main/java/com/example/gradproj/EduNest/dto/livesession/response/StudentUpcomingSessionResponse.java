package com.example.gradproj.EduNest.dto.livesession.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentUpcomingSessionResponse {
    private UUID sessionId;
    private String sessionName;
    private String mentorName;
    private UUID mentorshipId;
    private String mentorshipName;
    private UUID weekId;
    private String weekTitle;
    private LocalDateTime sessionStartDate;
    private String meetingUrl;
}
