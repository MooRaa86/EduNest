package com.example.gradproj.EduNest.dto.admin.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignAdminBadgeRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Badge ID is required")
    private Long badgeId;

    private String recognitionNote;
}
