package com.example.gradproj.EduNest.dto.admin.response;

import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminBadgeResponse {
    private UUID id;
    private UUID userId;
    private String userFullName;
    private UUID badgeId;
    private String badgeName;
    private String badgeDescription;
    private AdminBadgeType badgeType;
    private String recognitionNote;
    private LocalDateTime awardedAt;
}
