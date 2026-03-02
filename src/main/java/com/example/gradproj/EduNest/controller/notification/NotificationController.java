package com.example.gradproj.EduNest.controller.notification;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.dto.notification.NotificationSendRequest;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(
        name = "Notifications",
        description = "notifications rest apis"
)
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "get notifications for authenticated user")
    public ResponseEntity<PageResponse<NotificationDto>> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){

        String email = authentication.getName();
        PageResponse<NotificationDto> notifications =
                notificationService.getUserNotifications(email, size, page);

        return ResponseEntity.ok(notifications);
    }


    @GetMapping("/unread")
    @Operation(summary = "get unread notifications for authenticated user")
    public ResponseEntity<PageResponse<NotificationDto>> getUnreadNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){

        String email = authentication.getName();

        PageResponse<NotificationDto> notifications =
                notificationService.getUnreadNotificationsForUser(email, size, page);

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "get count of unread notifications for user")
    public long getUnreadCount(Authentication authentication){

        String email = authentication.getName();

        return notificationService.getUnreadCount(email);
    }

    @PatchMapping("/mark-all-read")
    @Operation(summary = "mark all notifications as read")
    public void markAllAsRead(Authentication authentication){

        String email = authentication.getName();

        notificationService.markAllAsRead(email);
    }


    @PatchMapping("/{id}/mark-read")
    @Operation(summary = "mark notification as read")
    public void markOneAsRead(
            @PathVariable Long id
    ){
        notificationService.markOneAsRead(id);
    }

    @PostMapping("/send")
    @Operation(summary = "send notification to user by email")
    public void sendNotification(
            @RequestBody @Valid NotificationSendRequest request
    ){

        notificationService.sendToUser(
                request.getEmail(),
                request.getTitle(),
                request.getContent(),
                request.getType()
        );
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "delete notification by id")
    public ResponseEntity<SimpleResponse> deleteNotification(@PathVariable Long id ){
        notificationService.deleteNotification(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("status","notification deleted successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @DeleteMapping("/delete-for-user")
    @Operation(summary = "delete all notifications for user")
    public ResponseEntity<SimpleResponse> deleteAllNotifications(Authentication authentication){
        String email = authentication.getName();
        notificationService.deleteAllNotificationsForUser(email);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("status","notifications deleted successfully");
        return ResponseEntity.ok(simpleResponse);
    }
}
