package com.example.gradproj.EduNest.controller.profile;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.profile.MentorProfileForStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/mentor")
@RequiredArgsConstructor
@Tag(name = "Mentor Profile For Student", description = "APIs for mentor profile viewed by students")
public class MentorProfileForStudent {

    private final MentorProfileForStudentService mentorProfileService;

    @GetMapping("/{mentorEmail}")
    @Operation(summary = "Get mentor basic profile information")
    public ResponseEntity<SimpleResponse> getMentorProfile(@PathVariable String mentorEmail) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorProfile", mentorProfileService.getMentorProfile(mentorEmail));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mentorEmail}/mentorships")
    @Operation(summary = "Get mentor mentorships with pagination")
    public ResponseEntity<SimpleResponse> getMentorMentorships(
            @PathVariable String mentorEmail,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "0") int page) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorships", mentorProfileService.getMentorMentorships(mentorEmail, size, page));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mentorEmail}/reviews")
    @Operation(summary = "Get mentor reviews with pagination")
    public ResponseEntity<SimpleResponse> getMentorReviews(
            @PathVariable String mentorEmail,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "0") int page) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("reviews", mentorProfileService.getMentorReviews(mentorEmail, size, page));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mentorEmail}/full")
    @Operation(summary = "Get complete mentor profile with mentorships and reviews")
    public ResponseEntity<SimpleResponse> getMentorFullProfile(
            @PathVariable String mentorEmail,
            @RequestParam(defaultValue = "5") int msSize,
            @RequestParam(defaultValue = "0") int msPage,
            @RequestParam(defaultValue = "5") int reviewsSize,
            @RequestParam(defaultValue = "0") int reviewsPage) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("profile", mentorProfileService.getMentorFullProfile(mentorEmail, msSize, msPage, reviewsSize, reviewsPage));
        return ResponseEntity.ok(response);
    }
}
