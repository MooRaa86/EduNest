package com.example.gradproj.EduNest.dto.dashboard;

import java.time.LocalDateTime;

public record EnrollmentPaymentResponse(
        String studentFullName,
        String studentEmail,
        String studentProfileImageUrl,
        LocalDateTime joinedDate,
        Double price,
        String mentorName,
        String mentorshipTitle,
        Double platformProfit
) {}
