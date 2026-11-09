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

    public static class AssignAdminBadgeRequestBuilder {
        public AssignAdminBadgeRequestBuilder userId(Long id) {
            this.userId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AssignAdminBadgeRequestBuilder userId(UUID id) {
            this.userId = id;
            return this;
        }
        public AssignAdminBadgeRequestBuilder badgeId(Long id) {
            this.badgeId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AssignAdminBadgeRequestBuilder badgeId(UUID id) {
            this.badgeId = id;
            return this;
        }
    }
}
