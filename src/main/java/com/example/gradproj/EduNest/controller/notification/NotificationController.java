package com.example.gradproj.EduNest.controller.notification;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.dto.notification.NotificationSendRequest;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
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
    public long getUnreadCount(Authentication authentication){

        String email = authentication.getName();

        return notificationService.getUnreadCount(email);
    }

    @PatchMapping("/mark-all-read")
    public void markAllAsRead(Authentication authentication){

        String email = authentication.getName();

        notificationService.markAllAsRead(email);
    }


    @PatchMapping("/{id}/mark-read")
    public void markOneAsRead(
            @PathVariable Long id
    ){
        notificationService.markOneAsRead(id);
    }

    @PostMapping("/send")
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
    public ResponseEntity<SimpleResponse> deleteNotification(@PathVariable Long id ){
        notificationService.deleteNotification(id);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("status","notification deleted successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @DeleteMapping("/delete-for-user")
    public ResponseEntity<SimpleResponse> deleteAllNotifications(Authentication authentication){
        String email = authentication.getName();
        notificationService.deleteAllNotificationsForUser(email);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("status","notifications deleted successfully");
        return ResponseEntity.ok(simpleResponse);
    }
}
