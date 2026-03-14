package com.example.gradproj.EduNest.dto.notification;

import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class NotificationDto {

    Long id;
    String title;
    String content;
    boolean isRead;
    NotificationType type;
    LocalDateTime time;
}
