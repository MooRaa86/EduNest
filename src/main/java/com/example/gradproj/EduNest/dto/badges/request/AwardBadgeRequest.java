package com.example.gradproj.EduNest.dto.badges.request;

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
public class AwardBadgeRequest {

    @NotNull
    private UUID studentId;

    private String note;

    public static class AwardBadgeRequestBuilder {
        public AwardBadgeRequestBuilder studentId(Long id) {
            this.studentId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AwardBadgeRequestBuilder studentId(UUID id) {
            this.studentId = id;
            return this;
        }
    }

    public void setStudentId(Long id) {
        this.studentId = id != null ? new UUID(0L, id) : null;
    }

    public void setStudentId(UUID id) {
        this.studentId = id;
    }

}
