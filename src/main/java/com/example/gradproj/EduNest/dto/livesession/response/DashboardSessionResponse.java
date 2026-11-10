package com.example.gradproj.EduNest.dto.livesession.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class DashboardSessionResponse {
    public UUID id;
    public String title;
    public String mentorshipTitle;
    public String weekTitle;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionStartDate;

    public DashboardSessionResponse(UUID id, String title, LocalDateTime sessionStartDate, String weekTitle, String mentorshipTitle) {
        this.id = id;
        this.title = title;
        this.sessionStartDate = sessionStartDate;
        this.weekTitle = weekTitle;
        this.mentorshipTitle = mentorshipTitle;
    }
}
