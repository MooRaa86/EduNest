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

    /**
     * @deprecated Transitional dual-identifier endpoint (EB-88).
     * Accepts numeric Long ID (primary) or legacy email (fallback).
     * Will transition to publicId UUID in EB-109.
     */
    @GetMapping("/{mentorIdentifier}")
    @Operation(
            summary = "Get mentor basic profile information",
            description = "Supports numeric mentor ID (primary) or legacy email. Migrating to UUID in EB-109."
    )
    public ResponseEntity<SimpleResponse> getMentorProfile(@PathVariable String mentorIdentifier) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorProfile", mentorProfileService.getMentorProfile(mentorIdentifier));
        return ResponseEntity.ok(response);
    }

    /**
     * @deprecated Transitional dual-identifier endpoint (EB-88).
     */
    @GetMapping("/{mentorIdentifier}/mentorships")
    @Operation(
            summary = "Get mentor mentorships with pagination",
            description = "Supports numeric mentor ID (primary) or legacy email. Migrating to UUID in EB-109."
    )
    public ResponseEntity<SimpleResponse> getMentorMentorships(
            @PathVariable String mentorIdentifier,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "0") int page) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorships", mentorProfileService.getMentorMentorships(mentorIdentifier, size, page));
        return ResponseEntity.ok(response);
    }

    /**
     * @deprecated Transitional dual-identifier endpoint (EB-88).
     */
    @GetMapping("/{mentorIdentifier}/reviews")
    @Operation(
            summary = "Get mentor reviews with pagination",
            description = "Supports numeric mentor ID (primary) or legacy email. Migrating to UUID in EB-109."
    )
    public ResponseEntity<SimpleResponse> getMentorReviews(
            @PathVariable String mentorIdentifier,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "0") int page) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("reviews", mentorProfileService.getMentorReviews(mentorIdentifier, size, page));
        return ResponseEntity.ok(response);
    }

    /**
     * @deprecated Transitional dual-identifier endpoint (EB-88).
     */
    @GetMapping("/{mentorIdentifier}/full")
    @Operation(
            summary = "Get complete mentor profile with mentorships and reviews",
            description = "Supports numeric mentor ID (primary) or legacy email. Migrating to UUID in EB-109."
    )
    public ResponseEntity<SimpleResponse> getMentorFullProfile(
            @PathVariable String mentorIdentifier,
            @RequestParam(defaultValue = "5") int msSize,
            @RequestParam(defaultValue = "0") int msPage,
            @RequestParam(defaultValue = "5") int reviewsSize,
            @RequestParam(defaultValue = "0") int reviewsPage) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("profile", mentorProfileService.getMentorFullProfile(mentorIdentifier, msSize, msPage, reviewsSize, reviewsPage));
        return ResponseEntity.ok(response);
    }
}
