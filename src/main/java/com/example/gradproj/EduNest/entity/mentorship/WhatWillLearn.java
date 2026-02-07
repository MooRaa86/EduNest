package com.example.gradproj.EduNest.entity.mentorship;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mentorship_what_will_learn")
public class WhatWillLearn extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private MentorShip mentorShip;
}
