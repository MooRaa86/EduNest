package com.example.gradproj.EduNest.entity.admin;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "user_admin_badges",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "admin_badge_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class UserAdminBadge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_badge_id", nullable = false)
    private AdminBadge adminBadge;

    @Column(name = "recognition_note", columnDefinition = "TEXT")
    private String recognitionNote;

    @Column(name = "awarded_at", updatable = false)
    private java.time.LocalDateTime awardedAt;
}
