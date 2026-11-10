package com.example.gradproj.EduNest.repository.admin;

import com.example.gradproj.EduNest.entity.admin.AdminBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminBadgeRepository extends JpaRepository<AdminBadge, Long> {

    Optional<AdminBadge> findByPublicId(UUID publicId);
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT ab.id FROM AdminBadge ab WHERE ab.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

}
