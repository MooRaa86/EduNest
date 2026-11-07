package com.example.gradproj.EduNest.repository.auth;

import com.example.gradproj.EduNest.entity.auth.RefreshTokenSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    Optional<RefreshTokenSession> findByAccessTokenJti(String accessTokenJti);

    List<RefreshTokenSession> findByFamilyId(String familyId);

    @Modifying
    @Query("""
        UPDATE RefreshTokenSession s
        SET s.revoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason
        WHERE s.familyId = :familyId AND s.revoked = false
    """)
    int revokeFamily(
            @Param("familyId") String familyId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("""
        UPDATE RefreshTokenSession s
        SET s.revoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason
        WHERE s.user.id = :userId AND s.revoked = false
    """)
    int revokeAllByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("""
        UPDATE RefreshTokenSession s
        SET s.revoked = true, s.revokedAt = :revokedAt, s.revokedReason = :reason
        WHERE s.admin.id = :adminId AND s.revoked = false
    """)
    int revokeAllByAdminId(
            @Param("adminId") Long adminId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("DELETE FROM RefreshTokenSession s WHERE s.expiresAt < :cutoff OR s.familyExpiresAt < :cutoff")
    int deleteExpiredSessions(@Param("cutoff") LocalDateTime cutoff);
}
