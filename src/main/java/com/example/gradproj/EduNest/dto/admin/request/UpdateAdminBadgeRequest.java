package com.example.gradproj.EduNest.dto.admin.request;

import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateAdminBadgeRequest {
    private String name;
    private String description;
    private AdminBadgeType type;
}
