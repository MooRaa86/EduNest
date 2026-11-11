package com.example.gradproj.EduNest.dto.badges.response;

import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeResponse {
    private UUID id;
    private UUID mentorshipId;
    private String title;
    private BadgeCategory category;
    private String description;
    private int points;
}
