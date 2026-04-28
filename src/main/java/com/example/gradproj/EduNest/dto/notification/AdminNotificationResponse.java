package com.example.gradproj.EduNest.dto.notification;

import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminNotificationResponse {
    private Long id;
    private String title;
    private String content;
    private NotificationType type;
    private LocalDateTime createdAt;
}
