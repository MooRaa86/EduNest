package com.example.gradproj.EduNest.repository.admin.projection;

import java.time.LocalDateTime;

/**
 * Projection for retrieving user admin badge details with all related information.
 * This projection avoids N+1 queries by fetching all required data in a single query.
 */
public interface UserAdminBadgeDetailProjection {
    Long getId();
    Long getUserId();
    String getUserFullName();
    Long getBadgeId();
    String getBadgeName();
    String getBadgeDescription();
    String getBadgeType();
    String getRecognitionNote();
    LocalDateTime getAwardedAt();
}

