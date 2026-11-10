package com.example.gradproj.EduNest.dto.notification;

import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class NotificationDto {

    UUID id;
    String title;
    String content;
    boolean isRead;
    NotificationType type;
    LocalDateTime time;
}
