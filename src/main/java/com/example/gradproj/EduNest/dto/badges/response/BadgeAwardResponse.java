package com.example.gradproj.EduNest.dto.badges.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BadgeAwardResponse {
    private Long id;
    private Long badgeId;
    private String badgeTitle;
    private Long studentId;
    private String studentFullName;
    private Long awardedById;
    private LocalDateTime awardedAt;
    private String note;
    private int badgePoints;
}
