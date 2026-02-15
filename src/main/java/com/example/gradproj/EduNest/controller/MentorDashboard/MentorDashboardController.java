package com.example.gradproj.EduNest.controller.MentorDashboard;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(
        name = "Mentor Dashboard",
        description = "Cards, Sessions, Reviews and other details in the dashboard"
)
@RequiredArgsConstructor
public class MentorDashboardController {

    private final DashboardService mentorDashboardService;

    @GetMapping("/cards")
    @Operation(summary = "get cards data")
    public ResponseEntity<SimpleResponse> getDashboardCardsDetails() {
        Map<String, Object> Cards = mentorDashboardService.getDashboardCardsDetails();
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("cards", Cards);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/reviews")
    @Operation(summary = "get reviews for the mentor")
    public ResponseEntity<SimpleResponse> getReviewsInDashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        PageResponse<ReviewsRsponse> reviews =
                mentorDashboardService.getReviewsInDashboard(page, size);

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("reviews", reviews);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/sessions")
    @Operation(summary = "get upcoming sessions")
    public ResponseEntity<SimpleResponse> getUpcomingSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        PageResponse<DashboardSessionResponse> sessions =
                mentorDashboardService
                        .getUpcomingSessionsForDashboard(page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("sessions", sessions);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/revenue-chart")
    @Operation(summary = "get sales data")
    public ResponseEntity<SimpleResponse> getRevenueChart(
            @RequestParam(required = false) Integer months
    ) {
        List<DashboardService.SalesChartResponse> chartDetails = mentorDashboardService.getSalesChartData(months);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("sales-chart", chartDetails);
        return ResponseEntity.ok(resp);
    }
}

