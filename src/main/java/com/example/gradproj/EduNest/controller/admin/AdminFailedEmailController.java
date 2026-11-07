package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.email.FailedEmailDetailResponse;
import com.example.gradproj.EduNest.dto.email.FailedEmailResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import com.example.gradproj.EduNest.service.email.EmailRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * EB-62 / INT-02: Administrative REST controller for email delivery observability and manual retry/resolution.
 */
@RestController
@RequestMapping("/api/v1/admin/failed-emails")
@RequiredArgsConstructor
@Tag(name = "Admin Failed Emails", description = "Operational observability APIs for inspecting and retrying failed email deliveries")
public class AdminFailedEmailController {

    private final EmailRetryService emailRetryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List failed email deliveries", description = "Retrieve paginated list of failed emails with optional status filter")
    public ResponseEntity<PageResponse<FailedEmailResponse>> getFailedEmails(
            @RequestParam(required = false) FailedEmailStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<FailedEmailResponse> response = emailRetryService.getFailedEmails(status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get failed email detail", description = "Retrieve full inspection details including html body for a failed email")
    public ResponseEntity<FailedEmailDetailResponse> getFailedEmailDetail(@PathVariable UUID publicId) {
        FailedEmailDetailResponse response = emailRetryService.getFailedEmailDetail(publicId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{publicId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manual email retry", description = "Trigger an immediate delivery attempt for a failed email")
    public ResponseEntity<SimpleResponse> retryFailedEmail(
            @PathVariable UUID publicId,
            Authentication authentication
    ) {
        String adminEmail = authentication != null ? authentication.getName() : "ADMIN";
        boolean success = emailRetryService.manualRetry(publicId, adminEmail);

        SimpleResponse response = new SimpleResponse();
        if (success) {
            response.addMessage("status", "Email re-sent successfully");
            response.addMessage("delivery", "DELIVERED");
        } else {
            response.addMessage("status", "Retry attempt failed");
            response.addMessage("delivery", "FAILED");
        }
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resolve failed email", description = "Mark a failed email as resolved/dismissed without sending")
    public ResponseEntity<SimpleResponse> resolveFailedEmail(
            @PathVariable UUID publicId,
            Authentication authentication
    ) {
        String adminEmail = authentication != null ? authentication.getName() : "ADMIN";
        emailRetryService.resolve(publicId, adminEmail);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Failed email record marked as RESOLVED");
        return ResponseEntity.ok(response);
    }
}
