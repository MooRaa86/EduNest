package com.example.gradproj.EduNest.repository.admin;

import com.example.gradproj.EduNest.entity.admin.UserAdminBadge;
import com.example.gradproj.EduNest.repository.admin.projection.UserAdminBadgeDetailProjection;
import com.example.gradproj.EduNest.repository.users.projection.AdminBadgeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAdminBadgeRepository extends JpaRepository<UserAdminBadge, Long> {

    @Query("SELECT e FROM UserAdminBadge e WHERE e.publicId = :publicId")
    Optional<UserAdminBadge> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<UserAdminBadge> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<UserAdminBadge> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT uab.id FROM UserAdminBadge uab WHERE uab.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(UserAdminBadge::getId);
        }
        return Optional.empty();
    }

    @Query("""
        SELECT
            uab.publicId as id,
            uab.user.publicId as userId,
            CONCAT(uab.user.firstName, ' ', uab.user.lastName) as userFullName,
            uab.adminBadge.publicId as badgeId,
            uab.adminBadge.name as badgeName,
            uab.adminBadge.description as badgeDescription,
            CAST(uab.adminBadge.type as string) as badgeType,
            uab.recognitionNote as recognitionNote,
            uab.awardedAt as awardedAt
        FROM UserAdminBadge uab
        WHERE uab.user.publicId = :userPublicId
        ORDER BY uab.awardedAt DESC
        """)
    List<UserAdminBadgeDetailProjection> findUserAdminBadgesByUserPublicIdOptimized(@Param("userPublicId") UUID userPublicId);

    boolean existsByUserIdAndAdminBadgeId(Long userId, Long badgeId);
    boolean existsByAdminBadgeId(Long badgeId);

    @Query("""
        SELECT
            uab.adminBadge.publicId as id,
            uab.adminBadge.name as name,
            uab.adminBadge.description as description,
            uab.adminBadge.type as type
        FROM UserAdminBadge uab
        WHERE uab.user.id = :userId
        """)
    List<AdminBadgeProjection> findAdminBadgesByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT
            uab.publicId as id,
            uab.user.publicId as userId,
            CONCAT(uab.user.firstName, ' ', uab.user.lastName) as userFullName,
            uab.adminBadge.publicId as badgeId,
            uab.adminBadge.name as badgeName,
            uab.adminBadge.description as badgeDescription,
            CAST(uab.adminBadge.type as string) as badgeType,
            uab.recognitionNote as recognitionNote,
            uab.awardedAt as awardedAt
        FROM UserAdminBadge uab
        WHERE uab.user.id = :userId
        ORDER BY uab.awardedAt DESC
        """)
    List<UserAdminBadgeDetailProjection> findUserAdminBadgesByIdOptimized(@Param("userId") Long userId);
}
