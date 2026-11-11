package com.example.gradproj.EduNest.dto.studentAchievement;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BadgeAchievementResponse {
    private UUID id;
    private String title;
    private String description;
    private int points;
    private UUID mentorshipId;
    private String mentorshipTitle;
    private String awardedByFullName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime awardedAt;
}
