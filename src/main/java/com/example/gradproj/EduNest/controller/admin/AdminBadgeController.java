package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.admin.request.AssignAdminBadgeRequest;
import com.example.gradproj.EduNest.dto.admin.request.CreateAdminBadgeRequest;
import com.example.gradproj.EduNest.dto.admin.request.UpdateAdminBadgeRequest;
import com.example.gradproj.EduNest.service.admin.AdminBadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "admin-badges", description = "APIs for managing admin badges and assigning them to users")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final AdminBadgeService adminBadgeService;

    @PostMapping("/api/admin/badges")
    @Operation(summary = "Create a new admin badge")
    public ResponseEntity<SimpleResponse> createBadge(@Valid @RequestBody CreateAdminBadgeRequest req) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin badge created successfully");
        response.addMessage("badge", adminBadgeService.createAdminBadge(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/api/admin/badges/{badgeId}")
    @Operation(summary = "Update an admin badge")
    public ResponseEntity<SimpleResponse> updateBadge(
            @PathVariable Long badgeId,
            @Valid @RequestBody UpdateAdminBadgeRequest req) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin badge updated successfully");
        response.addMessage("badge", adminBadgeService.updateAdminBadge(badgeId, req));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/admin/badges/delete/{badgeId}")
    @Operation(summary = "Delete an admin badge (only if never assigned)")
    public ResponseEntity<SimpleResponse> deleteBadge(@PathVariable Long badgeId) {
        adminBadgeService.deleteAdminBadge(badgeId);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin badge deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/admin/badges/assign")
    @Operation(summary = "Assign an admin badge to a user")
    public ResponseEntity<SimpleResponse> assignBadge(@Valid @RequestBody AssignAdminBadgeRequest req) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge assigned successfully");
        response.addMessage("assignment", adminBadgeService.awardBadgeToUser(
                req.getUserId(), req.getBadgeId(), req.getRecognitionNote()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/users/{userId}/badges")
    @Operation(summary = "Get all admin badges assigned to a user")
    public ResponseEntity<SimpleResponse> getUserBadges(@PathVariable Long userId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "User badges retrieved successfully");
        response.addMessage("badges", adminBadgeService.getUserBadges(userId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/admin/badges/{userBadgeId}")
    @Operation(summary = "Remove an admin badge from a user")
    public ResponseEntity<SimpleResponse> removeBadge(@PathVariable Long userBadgeId) {
        adminBadgeService.removeBadgeFromUser(userBadgeId);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Badge removed successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/admin/badges")
    @Operation(summary = "Get all available admin badges")
    public ResponseEntity<SimpleResponse> getAllBadges() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Admin badges retrieved successfully");
        response.addMessage("badges", adminBadgeService.getAllAvailableBadges());
        return ResponseEntity.ok(response);
    }
}
