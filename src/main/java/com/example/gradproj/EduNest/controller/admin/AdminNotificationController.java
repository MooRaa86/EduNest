package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationSendRequest;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications", description = "APIs for managing admin notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all admin notifications", description = "Retrieve paginated list of all admin notifications")
    public ResponseEntity<PageResponse<AdminNotificationResponse>> getAdminNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<AdminNotificationResponse> notifications = notificationService.getAdminNotifications(size, page);
        return ResponseEntity.ok(notifications);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete admin notification by id", description = "Delete a specific admin notification by its ID")
    public ResponseEntity<SimpleResponse> deleteAdminNotification(@PathVariable Long id) {
        notificationService.deleteAdminNotification(id);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Admin notification deleted successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all admin notifications", description = "Delete all admin notifications at once")
    public ResponseEntity<SimpleResponse> deleteAllAdminNotifications() {
        notificationService.deleteAllAdminNotifications();
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "All admin notifications deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send notification to admin", description = "Send a notification to the admin (for testing purposes)")
    public ResponseEntity<SimpleResponse> sendToAdmin(@RequestBody @Valid AdminNotificationSendRequest request) {
        notificationService.sendToAdmin(request.getTitle(), request.getContent(), request.getType());
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Notification sent to admin successfully");
        return ResponseEntity.ok(response);
    }
}
