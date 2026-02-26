package com.example.gradproj.EduNest.repository.notification.projection;

import com.example.gradproj.EduNest.enums.notification.NotificationType;

public interface NotificationProjection {
    Long getId();
    String getTitle();
    String getContent();
    boolean isRead();
    NotificationType getType();
}