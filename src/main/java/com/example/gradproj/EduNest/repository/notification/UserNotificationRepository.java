package com.example.gradproj.EduNest.repository.notification;

import com.example.gradproj.EduNest.entity.notification.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserNotificationRepository
        extends JpaRepository<UserNotification, Long> {

    @Query("""
        select u
        from UserNotification u
        join fetch u.notification
        where u.user.email = :email
        order by u.createdAt desc
    """)
    Page<UserNotification> findWithNotification(
            String email,
            Pageable pageable
    );

    @Query("""
        select u
        from UserNotification u
        join fetch u.notification
        where u.user.email = :email
        and u.isRead = false
        order by u.createdAt desc
    """)
    Page<UserNotification> findUnreadWithNotification(
            String email,
            Pageable pageable
    );

    long countByUserEmailAndIsReadFalse(String email);

    @Modifying
    @Query("""
        update UserNotification u
        set u.isRead = true
        where u.user.email = :email
    """)
    void markAllAsRead(String email);

    @Modifying
    @Query("""
        update UserNotification u
        set u.isRead = true
        where u.id = :id
    """)
    void markOneAsRead(Long id);

    void deleteAllByUserEmail(String email);
}