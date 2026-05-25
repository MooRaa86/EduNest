package com.example.gradproj.EduNest.controller.badges;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.badges.request.CreateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.request.UpdateBadgeRequest;
import com.example.gradproj.EduNest.service.badges.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/badges")
@Tag(name = "badges", description = "APIs for managing Badges (create, delete, get by mentorship)")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @PostMapping("/mentorship/{mentorshipId}")
    @Operation(summary = "Create a badge for a specific mentorship")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> create(
            @PathVariable Long mentorshipId,
            @Valid @RequestBody CreateBadgeRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge created successfully");
        response.addMessage("badge", badgeService.createBadge(mentorshipId, req));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{badgeId}")
    @Operation(summary = "Get a badge by id")
    public ResponseEntity<SimpleResponse> getById(@PathVariable Long badgeId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge retrieved successfully");
        response.addMessage("badge", badgeService.getBadgeById(badgeId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mentorship/{mentorshipId}")
    @Operation(summary = "Get all badges for a specific mentorship")
    public ResponseEntity<SimpleResponse> getByMentorship(@PathVariable Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badges retrieved successfully");
        response.addMessage("badges", badgeService.getBadgesByMentorship(mentorshipId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{badgeId}")
    @Operation(summary = "Update a badge")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> update(
            @PathVariable Long badgeId,
            @Valid @RequestBody UpdateBadgeRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge updated successfully");
        response.addMessage("badge", badgeService.updateBadge(badgeId, req));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{badgeId}")
    @Operation(summary = "Delete a badge (only if never awarded)")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> delete(@PathVariable Long badgeId) {
        badgeService.deleteBadge(badgeId);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge deleted successfully");
        return ResponseEntity.ok(response);
    }
}
