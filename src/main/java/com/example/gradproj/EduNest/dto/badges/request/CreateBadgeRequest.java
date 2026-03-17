package com.example.gradproj.EduNest.dto.badges.request;

import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateBadgeRequest {

    @NotBlank
    private String title;

    @NotNull
    private BadgeCategory category;

    @NotBlank
    private String description;

    @Min(1) @Max(500)
    private int points;
}
