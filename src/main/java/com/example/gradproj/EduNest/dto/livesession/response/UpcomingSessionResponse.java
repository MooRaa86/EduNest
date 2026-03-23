package com.example.gradproj.EduNest.dto.livesession.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class UpcomingSessionResponse {
    private Long id;
    private String title;
    private Long mentorshipId;
    private String mentorshipTitle;
    private Long weekId;
    private String weekTitle;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionStartDate;

    public UpcomingSessionResponse(Long id, String title, LocalDateTime sessionStartDate, Long weekId, String weekTitle, Long mentorshipId, String mentorshipTitle) {
        this.id = id;
        this.title = title;
        this.sessionStartDate = sessionStartDate;
        this.weekId = weekId;
        this.weekTitle = weekTitle;
        this.mentorshipId = mentorshipId;
        this.mentorshipTitle = mentorshipTitle;
    }
}
