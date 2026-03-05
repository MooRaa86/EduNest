package com.example.gradproj.EduNest.dto.livesession.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class DashboardSessionResponse {
    public String title;
    public String mentorshipTitle;
    public String weekTitle;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionStartDate;

    public DashboardSessionResponse(String title, LocalDateTime sessionStartDate, String weekTitle, String mentorshipTitle) {
        this.title = title;
        this.sessionStartDate = sessionStartDate;
        this.weekTitle = weekTitle;
        this.mentorshipTitle = mentorshipTitle;
    }

}
