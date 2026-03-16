package com.example.gradproj.EduNest.dto.badges.request;

import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateBadgeRequest {

    private String title;
    private BadgeCategory category;
    private String description;

    @Min(1) @Max(500)
    private Integer points;
}
