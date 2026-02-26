package com.example.gradproj.EduNest.controller.MentorDashboard;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.dashboard.DashboardCardsResponse;
import com.example.gradproj.EduNest.dto.dashboard.MentorDashboardResponse;
import com.example.gradproj.EduNest.dto.dashboard.MentorshipDashboardResponse;
import com.example.gradproj.EduNest.dto.dashboard.SalesChartResponse;
import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.ReviewsRsponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorShipListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorStudentListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipStatsResponse;
import com.example.gradproj.EduNest.repository.points.projection.TopStudentResponse;
import com.example.gradproj.EduNest.service.dashboard.DashboardService;
import com.example.gradproj.EduNest.service.dashboard.MentorshipDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(
        name = "Mentor Dashboards",
        description = "Cards, Sessions, Reviews and other details in the dashboard & mentorship dashboard apis"
)
@RequiredArgsConstructor
public class MentorDashboardController {

    private final DashboardService mentorDashboardService;
    private final MentorshipDashboardService mentorshipDashboardService;

    @GetMapping("/cards")
    @Operation(summary = "get cards data")
    public ResponseEntity<SimpleResponse> getDashboardCardsDetails() {
        DashboardCardsResponse Cards = mentorDashboardService.getDashboardCardsDetails();
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("cards", Cards);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/reviews")
    @Operation(summary = "get reviews for the mentor")
    public ResponseEntity<SimpleResponse> getReviewsInDashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
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
        List<SalesChartResponse> chartDetails = mentorDashboardService.getSalesChartData(months);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("sales-chart", chartDetails);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mentorships")
    @Operation(summary = "All mentorship (Details) page")
    public ResponseEntity<SimpleResponse> getMentorships(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "6") int size){
        PageResponse<MentorShipListResponse> mentorships = mentorshipDashboardService.getMentorMentorships(page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("mentorships", mentorships);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "name, status and cards details for a mentorship")
    public ResponseEntity<MentorshipStatsResponse> getStats(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(mentorshipDashboardService.getStats(id));
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "get mentorship dashboard reviews")
    public ResponseEntity<SimpleResponse> getReviews(
            @PathVariable("id") Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {

        PageResponse<ReviewsRsponse> reviews =
                mentorshipDashboardService.getReviewsForMentorship(page, size, mentorshipId);

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("reviews", reviews);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/top-learners")
    @Operation(summary = "find top learners for a mentorship")
    public ResponseEntity<SimpleResponse> findTopLearners(
            @PathVariable("id") Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
    ) {
        PageResponse<TopStudentResponse> students = mentorshipDashboardService
                .findTopLearnersByMentorshipId(mentorshipId, page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("top-learners", students);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/students")
    @Operation(summary = "get all mentor subscribed students")
    public ResponseEntity<SimpleResponse> getStudents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        PageResponse<MentorStudentListResponse> students =
                mentorDashboardService.getStudents(page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("students", students);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    @Operation(summary = "get full mentor dashboard")
    public ResponseEntity<SimpleResponse> getDashboard(

            @RequestParam(defaultValue = "0") int reviewPage,
            @RequestParam(defaultValue = "6") int reviewSize,

            @RequestParam(defaultValue = "0") int sessionPage,
            @RequestParam(defaultValue = "5") int sessionSize,

            @RequestParam(defaultValue = "0") int notificationPage,
            @RequestParam(defaultValue = "3") int notificationSize,

            @RequestParam(required = false) Integer months
    ) {

        MentorDashboardResponse dashboard =
                mentorDashboardService.getFullDashboard(
                        reviewPage,
                        reviewSize,
                        sessionPage,
                        sessionSize,
                        notificationSize,
                        notificationPage,
                        months
                );

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("dashboard", dashboard);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mentorship/{id}")
    @Operation(summary = "get full mentorship dashboard")
    public ResponseEntity<SimpleResponse> getMentorshipDashboard(

            @PathVariable Long id,

            @RequestParam(defaultValue = "0") int reviewsPage,
            @RequestParam(defaultValue = "6") int reviewsSize,

            @RequestParam(defaultValue = "0") int topPage,
            @RequestParam(defaultValue = "3") int topSize
    ) {

        MentorshipDashboardResponse dashboard =
                mentorshipDashboardService
                        .getFullMentorshipDashboard(
                                id,
                                reviewsPage,
                                reviewsSize,
                                topPage,
                                topSize
                        );

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("dashboard", dashboard);

        return ResponseEntity.ok(resp);
    }
}

