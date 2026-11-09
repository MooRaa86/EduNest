package com.example.gradproj.EduNest.dto.notification;

import com.example.gradproj.EduNest.enums.notification.NotificationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminNotificationResponse {
    private UUID id;
    private String title;
    private String content;
    private NotificationType type;
    private LocalDateTime createdAt;

    public static class AdminNotificationResponseBuilder {
        public AdminNotificationResponseBuilder id(Long id) {
            this.id = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public AdminNotificationResponseBuilder id(UUID id) {
            this.id = id;
            return this;
        }
    }

}
