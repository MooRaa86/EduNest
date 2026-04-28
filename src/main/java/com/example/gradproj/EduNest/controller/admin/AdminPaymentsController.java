package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.dashboard.EnrollmentPaymentResponse;
import com.example.gradproj.EduNest.dto.dashboard.FullPaymentPageResponse;
import com.example.gradproj.EduNest.dto.dashboard.SalesChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.service.admin.Payments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments")
@Tag(name = "Admin Payments", description = "APIs for admin payments and revenue management")
@RequiredArgsConstructor
public class AdminPaymentsController {

    private final Payments paymentsService;

    @GetMapping("/sales-chart")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get sales chart", description = "Get monthly revenue data for chart visualization")
    public ResponseEntity<SimpleResponse> getSalesChart(
            @RequestParam(required = false) Integer months) {
        List<SalesChartResponse> chart = paymentsService.getAdminSalesChart(months);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Sales chart retrieved successfully");
        response.addMessage("chart", chart);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get enrollment payments", description = "Get paginated list of all enrollment payments with student and mentorship details")
    public ResponseEntity<SimpleResponse> getEnrollmentPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<EnrollmentPaymentResponse> payments = paymentsService.getEnrollmentPayments(page, size);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Enrollment payments retrieved successfully");
        response.addMessage("payments", payments);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/full")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get full payment page", description = "Get complete payment page data in one request (sales chart + enrollment payments)")
    public ResponseEntity<SimpleResponse> getFullPaymentPage(
            @RequestParam(required = false) Integer months,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        FullPaymentPageResponse fullPage = paymentsService.getFullPaymentPage(months, page, size);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Full payment page retrieved successfully");
        response.addMessage("data", fullPage);
        return ResponseEntity.ok(response);
    }
}
