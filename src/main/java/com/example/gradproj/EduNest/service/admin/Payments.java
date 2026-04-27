package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.dashboard.EnrollmentPaymentResponse;
import com.example.gradproj.EduNest.dto.dashboard.FullPaymentPageResponse;
import com.example.gradproj.EduNest.dto.dashboard.SalesChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MonthlyRevenueProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class Payments {

    private final EnrollmentRepository enrollmentRepository;

    @PreAuthorize("hasRole('ADMIN')")
    public List<SalesChartResponse> getAdminSalesChart(Integer months) {
        LocalDateTime startDate = null;
        if (months != null && months > 0) {
            startDate = LocalDateTime.now().minusMonths(months);
        }

        List<MonthlyRevenueProjection> data =
                enrollmentRepository.getMonthlyRevenueForAdmin(startDate);

        return data.stream()
                .map(p -> new SalesChartResponse(
                        Month.of(p.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        p.getYear(),
                        p.getTotalRevenue()
                ))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<EnrollmentPaymentResponse> getEnrollmentPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").descending());

        Page<EnrollmentPaymentResponse> result = enrollmentRepository.findAllEnrollmentPayments(pageable);

        return PageResponse.<EnrollmentPaymentResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public FullPaymentPageResponse getFullPaymentPage(Integer months, int page, int size) {
        List<SalesChartResponse> salesChart = getAdminSalesChart(months);
        PageResponse<EnrollmentPaymentResponse> enrollmentPayments = getEnrollmentPayments(page, size);

        return FullPaymentPageResponse.builder()
                .salesChart(salesChart)
                .enrollmentPayments(enrollmentPayments)
                .build();
    }

}
