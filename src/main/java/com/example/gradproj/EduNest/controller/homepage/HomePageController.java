package com.example.gradproj.EduNest.controller.homepage;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.homepage.StudentHomePageResponse;
import com.example.gradproj.EduNest.service.homepage.HomePageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/homepage")
@Tag(name = "Homepage", description = "APIs for student homepage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class HomePageController {
    private final HomePageService homePageService;

    @GetMapping("/full")
    @Operation(summary = "continue learning + upcoming items + recommended section")
    public ResponseEntity<SimpleResponse> getStudentUpcoming(Authentication authentication) {
        String email = authentication.getName();
        StudentHomePageResponse response = homePageService.getStudentHomePage(email);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "full home page retrived");
        simpleResponse.addMessage("data", response);
        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/progress/{mentorshipId}")
    @Operation(summary = "Get student progress for a specific mentorship (don't try)")
    public ResponseEntity<SimpleResponse> getStudentProgress(
            @PathVariable Long mentorshipId,
            Authentication authentication
    ) {
        String email = authentication.getName();
        var progress = homePageService.getStudentMentorshipProgress(email, mentorshipId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Progress retrieved successfully");
        simpleResponse.addMessage("progress", progress);
        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/recommended")
    @Operation(summary = "Get recommended mentorships for authenticated student based on enrolled categories, rating, and enrollment count (don't try)")
    public ResponseEntity<SimpleResponse> getRecommendedMentorships(
            Authentication authentication
    ) {
        String email = authentication.getName();
        var recommendations = homePageService.getRecommendedMentorships(email);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Recommended mentorships retrieved successfully");
        simpleResponse.addMessage("recommendations", recommendations);
        return ResponseEntity.ok(simpleResponse);
    }
}
