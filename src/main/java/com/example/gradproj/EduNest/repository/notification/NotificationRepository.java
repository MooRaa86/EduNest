package com.example.gradproj.EduNest.repository.notification;

import com.example.gradproj.EduNest.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;


public interface NotificationRepository
        extends JpaRepository<Notification,Long> {
}