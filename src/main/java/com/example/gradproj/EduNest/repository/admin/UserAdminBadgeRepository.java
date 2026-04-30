package com.example.gradproj.EduNest.repository.admin;

import com.example.gradproj.EduNest.entity.admin.UserAdminBadge;
import com.example.gradproj.EduNest.repository.admin.projection.UserAdminBadgeDetailProjection;
import com.example.gradproj.EduNest.repository.users.projection.AdminBadgeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAdminBadgeRepository extends JpaRepository<UserAdminBadge, Long> {
    boolean existsByUserIdAndAdminBadgeId(Long userId, Long badgeId);
    boolean existsByAdminBadgeId(Long badgeId);

    @Query("""
        SELECT
            uab.adminBadge.id as id,
            uab.adminBadge.name as name,
            uab.adminBadge.description as description,
            uab.adminBadge.type as type
        FROM UserAdminBadge uab
        WHERE uab.user.id = :userId
        """)
    List<AdminBadgeProjection> findAdminBadgesByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT
            uab.id as id,
            uab.user.id as userId,
            CONCAT(uab.user.firstName, ' ', uab.user.lastName) as userFullName,
            uab.adminBadge.id as badgeId,
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
