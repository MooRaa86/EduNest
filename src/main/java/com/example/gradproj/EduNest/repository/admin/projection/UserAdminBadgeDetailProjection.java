package com.example.gradproj.EduNest.repository.admin.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection for retrieving user admin badge details with all related information.
 * This projection avoids N+1 queries by fetching all required data in a single query.
 */
public interface UserAdminBadgeDetailProjection {
    UUID getId();
    UUID getUserId();
    String getUserFullName();
    UUID getBadgeId();
    String getBadgeName();
    String getBadgeDescription();
    String getBadgeType();
    String getRecognitionNote();
    LocalDateTime getAwardedAt();
}
