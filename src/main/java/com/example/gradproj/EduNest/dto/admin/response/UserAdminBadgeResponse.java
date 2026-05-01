package com.example.gradproj.EduNest.dto.admin.response;

import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserAdminBadgeResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private Long badgeId;
    private String badgeName;
    private String badgeDescription;
    private AdminBadgeType badgeType;
    private String recognitionNote;
    private LocalDateTime awardedAt;
}
