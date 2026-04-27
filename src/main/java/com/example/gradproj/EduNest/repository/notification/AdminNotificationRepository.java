package com.example.gradproj.EduNest.repository.notification;

import com.example.gradproj.EduNest.entity.notification.AdminNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification,Long> {
    Page<AdminNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
