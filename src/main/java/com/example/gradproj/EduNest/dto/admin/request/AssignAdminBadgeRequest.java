package com.example.gradproj.EduNest.dto.admin.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignAdminBadgeRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Badge ID is required")
    private UUID badgeId;

    private String recognitionNote;
}
