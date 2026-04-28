package com.example.gradproj.EduNest.dto.dashboard;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
public class FullPaymentPageResponse {
    private List<SalesChartResponse> salesChart;
    private PageResponse<EnrollmentPaymentResponse> enrollmentPayments;
}
