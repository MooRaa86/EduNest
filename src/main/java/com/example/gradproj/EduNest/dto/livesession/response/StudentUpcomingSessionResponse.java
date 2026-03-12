package com.example.gradproj.EduNest.dto.livesession.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentUpcomingSessionResponse {
    private Long sessionId;
    private String sessionName;
    private String mentorName;
    private Long mentorshipId;
    private String mentorshipName;
    private String weekTitle;
    private LocalDateTime sessionStartDate;
    private String meetingUrl;
}
