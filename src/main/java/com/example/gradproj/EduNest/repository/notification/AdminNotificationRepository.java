package com.example.gradproj.EduNest.repository.notification;

import com.example.gradproj.EduNest.entity.notification.AdminNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    Optional<AdminNotification> findByPublicId(UUID publicId);
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT an.id FROM AdminNotification an WHERE an.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    Page<AdminNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
