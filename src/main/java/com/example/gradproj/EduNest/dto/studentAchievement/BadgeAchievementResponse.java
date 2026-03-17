package com.example.gradproj.EduNest.dto.studentAchievement;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BadgeAchievementResponse {
    private Long id;
    private String title;
    private String description;
    private int points;
    private Long mentorshipId;
    private String mentorshipTitle;
    private String awardedByFullName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime awardedAt;
}
