package com.example.gradproj.EduNest.dto.badges.response;

import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BadgeResponse {
    private Long id;
    private Long mentorshipId;
    private String title;
    private BadgeCategory category;
    private String description;
    private int points;
}
