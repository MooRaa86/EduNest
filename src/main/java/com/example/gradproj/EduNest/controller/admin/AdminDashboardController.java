package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.dashboard.AdminDashboard.AdminDashboardCards;
import com.example.gradproj.EduNest.dto.dashboard.AdminDashboard.AdminFullDashDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.repository.livesession.projections.MonthlySessionsProjection;
import com.example.gradproj.EduNest.repository.users.projection.TopMentorProjection;
import com.example.gradproj.EduNest.service.admin.Dashboard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "APIs for admin dashboard data (cards, charts, top mentors, notifications)")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final Dashboard dashboardService;

    @GetMapping("/cards")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get dashboard cards", description = "Get all dashboard statistics cards (total students, mentors, mentorships, revenue)")
    public ResponseEntity<SimpleResponse> getDashboardCards() {
        AdminDashboardCards cards = dashboardService.getAdminDashboardCards();
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Dashboard cards retrieved successfully");
        response.addMessage("cards", cards);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions-chart")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get monthly sessions chart", description = "Get ended sessions grouped by month for chart visualization")
    public ResponseEntity<SimpleResponse> getMonthlySessionsChart(
            @RequestParam(required = false) Integer months) {
        List<MonthlySessionsProjection> chart = dashboardService.getMonthlySessionsForLastMonths(months);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Monthly sessions chart retrieved successfully");
        response.addMessage("chart", chart);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get admin notifications", description = "Get paginated admin notifications")
    public ResponseEntity<SimpleResponse> getAdminNotifications(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "0") int page) {
        PageResponse<AdminNotificationResponse> notifications = dashboardService.getAdminNotifications(size, page);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin notifications retrieved successfully");
        response.addMessage("notifications", notifications);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-mentors")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get top mentors", description = "Get top mentors ordered by total students enrolled")
    public ResponseEntity<SimpleResponse> getTopMentors(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "0") int page) {
        PageResponse<TopMentorProjection> topMentors = dashboardService.getTopMentorsByTotalStudents(size, page);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Top mentors retrieved successfully");
        response.addMessage("topMentors", topMentors);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/full")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get full dashboard content", description = "Get complete dashboard content in one request (cards, sessions chart, notifications, top mentors)")
    public ResponseEntity<SimpleResponse> getFullDashboard(
            @RequestParam(required = false) Integer months,
            @RequestParam(defaultValue = "10") int notificationSize,
            @RequestParam(defaultValue = "0") int notificationPage,
            @RequestParam(defaultValue = "10") int mentorSize,
            @RequestParam(defaultValue = "0") int mentorPage) {
        AdminFullDashDto fullDashboard = dashboardService.getFullDashboard(
                months, notificationSize, notificationPage, mentorSize, mentorPage
        );
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Full dashboard content retrieved successfully");
        response.addMessage("dashboard", fullDashboard);
        return ResponseEntity.ok(response);
    }
}
