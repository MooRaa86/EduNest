package com.example.gradproj.EduNest.repository.notification;

import com.example.gradproj.EduNest.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserEmailOrderByCreatedAtDesc(
            String email, Pageable pageable
    );

    long countByUserEmailAndIsReadFalse(String email);

    @Modifying
    @Query("""
        update Notification n
        set n.isRead = true
        where n.user.email = :email
        and n.isRead = false
    """)
    void markAllAsRead(String email);

    @Modifying
    @Query("""
        update Notification n
        set n.isRead = true
        where n.id = :id
    """)
    void markNotificationAsRead(@Param("id") Long id);

    Page<Notification> findByUserEmailAndIsReadFalseOrderByCreatedAtDesc(String email, Pageable pageable);
}