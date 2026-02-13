package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


@Setter @Getter @NoArgsConstructor @AllArgsConstructor
@SuperBuilder
@Entity
public class Tags extends BaseEntity {

    @Column(nullable = false)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id")
    private MentorShip mentorShip;
}
