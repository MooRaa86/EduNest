package com.example.gradproj.EduNest.dto.badges.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeAwardResponse {
    private UUID id;
    private UUID badgeId;
    private String badgeTitle;
    private UUID studentId;
    private String studentFullName;
    private UUID awardedById;
    private LocalDateTime awardedAt;
    private String note;
    private int badgePoints;
}
