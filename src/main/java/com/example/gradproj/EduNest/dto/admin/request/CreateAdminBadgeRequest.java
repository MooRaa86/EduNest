package com.example.gradproj.EduNest.dto.admin.request;

import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateAdminBadgeRequest {
    @NotBlank(message = "Badge name is required")
    private String name;

    @NotBlank(message = "Badge description is required")
    private String description;

    @NotNull(message = "Badge type is required")
    private AdminBadgeType type;
}
