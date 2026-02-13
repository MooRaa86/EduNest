package com.example.gradproj.EduNest.dto.livesession.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttendanceResponse {
    private Long sessionId;
    private Long studentId;
    private double attendancePercentage;
    private String status;
}
