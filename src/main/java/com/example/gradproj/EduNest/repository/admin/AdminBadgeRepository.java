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

    @Query("SELECT e FROM AdminBadge e WHERE e.publicId = :publicId")
    Optional<AdminBadge> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<AdminBadge> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<AdminBadge> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT ab.id FROM AdminBadge ab WHERE ab.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(AdminBadge::getId);
        }
        return Optional.empty();
    }

}
