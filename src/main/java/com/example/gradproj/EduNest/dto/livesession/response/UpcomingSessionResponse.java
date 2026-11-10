package com.example.gradproj.EduNest.dto.livesession.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class UpcomingSessionResponse {
    private UUID id;
    private String title;
    private UUID mentorshipId;
    private String mentorshipTitle;
    private UUID weekId;
    private String weekTitle;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionStartDate;

    public UpcomingSessionResponse(UUID id, String title, LocalDateTime sessionStartDate, UUID weekId, String weekTitle, UUID mentorshipId, String mentorshipTitle) {
        this.id = id;
        this.title = title;
        this.sessionStartDate = sessionStartDate;
        this.weekId = weekId;
        this.weekTitle = weekTitle;
        this.mentorshipId = mentorshipId;
        this.mentorshipTitle = mentorshipTitle;
    }
}
