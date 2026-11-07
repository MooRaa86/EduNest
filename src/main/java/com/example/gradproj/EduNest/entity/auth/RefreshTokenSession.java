package com.example.gradproj.EduNest.entity.auth;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * EB-91: Persistent store for rotating refresh token sessions.
 * Holds hashed refresh tokens, session families for replay defense,
 * and immutable owner bindings (User or Admin).
 */
@Entity
@Table(name = "refresh_token_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshTokenSession extends BaseEntity {

    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "access_token_jti", length = 64)
    private String accessTokenJti;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "family_expires_at", nullable = false)
    private LocalDateTime familyExpiresAt;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    /**
     * Checks if the session has expired either via sliding TTL or absolute family TTL.
     */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt) || now.isAfter(familyExpiresAt);
    }
}
