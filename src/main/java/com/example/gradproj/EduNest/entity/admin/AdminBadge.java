package com.example.gradproj.EduNest.entity.admin;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "admin_badges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class AdminBadge extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminBadgeType type;
}
