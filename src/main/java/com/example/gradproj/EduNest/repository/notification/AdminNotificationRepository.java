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

    @Query("SELECT e FROM AdminNotification e WHERE e.publicId = :publicId")
    Optional<AdminNotification> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<AdminNotification> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<AdminNotification> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT an.id FROM AdminNotification an WHERE an.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(AdminNotification::getId);
        }
        return Optional.empty();
    }

    Page<AdminNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
