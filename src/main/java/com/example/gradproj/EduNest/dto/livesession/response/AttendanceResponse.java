package com.example.gradproj.EduNest.dto.livesession.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AttendanceResponse {
    private UUID sessionId;
    private UUID studentId;
    private double attendancePercentage;
    private String status;

    public static class AttendanceResponseBuilder {
        public AttendanceResponseBuilder sessionId(Long id) {
            this.sessionId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AttendanceResponseBuilder sessionId(UUID id) {
            this.sessionId = id;
            return this;
        }
        public AttendanceResponseBuilder studentId(Long id) {
            this.studentId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AttendanceResponseBuilder studentId(UUID id) {
            this.studentId = id;
            return this;
        }
    }
}
