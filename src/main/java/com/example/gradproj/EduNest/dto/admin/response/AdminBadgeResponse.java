package com.example.gradproj.EduNest.dto.admin.response;

import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBadgeResponse {
    private Long id;
    private String name;
    private String description;
    private AdminBadgeType type;
}
