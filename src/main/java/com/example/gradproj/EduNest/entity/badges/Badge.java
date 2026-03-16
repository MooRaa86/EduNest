package com.example.gradproj.EduNest.entity.badges;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "badges")
@SuperBuilder
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Badge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorship;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BadgeCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Min(1) @Max(500)
    @Column(nullable = false)
    private int points;

    @OneToMany(mappedBy = "badge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BadgeAward> awards = new ArrayList<>();


}
