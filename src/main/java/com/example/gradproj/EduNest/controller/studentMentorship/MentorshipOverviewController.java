package com.example.gradproj.EduNest.controller.studentMentorship;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentMentorship.MentorshipOverviewDto;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipReviewProjection;
import com.example.gradproj.EduNest.service.studentMentorship.MentorshipOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/mentorships")
@Tag(name = "Student Mentorship", description = "APIs for student mentorship overview")
@RequiredArgsConstructor
public class MentorshipOverviewController {

    private final MentorshipOverviewService mentorshipOverviewService;

    @GetMapping("/{mentorshipId}")
    @Operation(summary = "Get mentorship overview with enrollment status without mentor ms")
    public ResponseEntity<SimpleResponse> getMentorshipOverview(
            @PathVariable Long mentorshipId,
            Authentication authentication) {
        String studentEmail = authentication.getName();
        MentorshipOverviewDto mentorship = mentorshipOverviewService.getMentorshipWithEnrollmentStatus(mentorshipId, studentEmail);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship overview retrieved successfully");
        response.addMessage("mentorship", mentorship);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mentorshipId}/reviews")
    @Operation(summary = "Get mentorship reviews with pagination")
    public ResponseEntity<SimpleResponse> getMentorshipReviews(
            @PathVariable Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String studentEmail = authentication.getName();
        PageResponse<MentorshipReviewProjection> reviews = mentorshipOverviewService
                .getMentorshipReviews(mentorshipId, studentEmail,page,size);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship reviews retrieved successfully");
        response.addMessage("reviews", reviews);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mentor/{mentorEmail}/top-mentorships")
    @Operation(summary = "Get top mentorships by mentor email ordered by rating")
    public ResponseEntity<SimpleResponse> getTop3MentorshipsByMentor(
            @PathVariable String mentorEmail,
            @RequestParam(defaultValue = "3") int limit,
            Authentication authentication) {
        String studentEmail = authentication.getName();
        List<MentorshipExploreDto> mentorships = mentorshipOverviewService.getTopMentorshipsByMentorEmail(mentorEmail, studentEmail, limit);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Top mentorships retrieved successfully");
        response.addMessage("mentorships", mentorships);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mentorshipId}/overview")
    @Operation(summary = "Get mentorship overview with top mentorships by same mentor")
    public ResponseEntity<SimpleResponse> getMentorshipWithTopMentorships(
            @PathVariable Long mentorshipId,
            @RequestParam(defaultValue = "3") int limit,
            Authentication authentication) {
        String studentEmail = authentication.getName();
        MentorshipOverviewDto mentorship = mentorshipOverviewService.getMentorshipWithEnrollmentStatus(mentorshipId, studentEmail);
        List<MentorshipExploreDto> topMentorships = mentorshipOverviewService.getTopMentorshipsByMentorEmail(mentorship.getMentorEmail(), studentEmail, limit);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship overview with top mentorships retrieved successfully");
        response.addMessage("mentorship", mentorship);
        response.addMessage("topMentorships", topMentorships);
        return ResponseEntity.ok(response);
    }
}
