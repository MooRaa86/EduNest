package com.example.gradproj.EduNest.controller.badges;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.badges.request.AwardBadgeRequest;
import com.example.gradproj.EduNest.service.badges.BadgeAwardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/badge-awards")
@Tag(name = "badge-awards", description = "APIs for awarding badges to students")
@RequiredArgsConstructor
public class BadgeAwardController {

    private final BadgeAwardService badgeAwardService;

    @PostMapping("/{badgeId}/award")
    @Operation(summary = "Award a badge to a student")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> award(
            @PathVariable Long badgeId,
            @Valid @RequestBody AwardBadgeRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge awarded successfully");
        response.addMessage("award", badgeAwardService.awardBadge(badgeId, req));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mentorship/{mentorshipId}")
    @Operation(summary = "Get all awards for a mentorship")
    public ResponseEntity<SimpleResponse> getByMentorship(@PathVariable Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Awards retrieved successfully");
        response.addMessage("awards", badgeAwardService.getAwardsByMentorship(mentorshipId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get all badge awards for a student")
    public ResponseEntity<SimpleResponse> getByStudent(@PathVariable Long studentId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Awards retrieved successfully");
        response.addMessage("awards", badgeAwardService.getAwardsByStudent(studentId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/badge/{badgeId}")
    @Operation(summary = "Get all students awarded a specific badge")
    public ResponseEntity<SimpleResponse> getByBadge(@PathVariable Long badgeId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Awards retrieved successfully");
        response.addMessage("awards", badgeAwardService.getAwardsByBadge(badgeId));
        return ResponseEntity.ok(response);
    }
}
