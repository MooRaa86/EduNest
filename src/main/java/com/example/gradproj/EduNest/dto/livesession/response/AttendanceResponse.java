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
}
