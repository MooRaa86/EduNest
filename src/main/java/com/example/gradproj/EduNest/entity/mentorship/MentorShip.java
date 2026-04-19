package com.example.gradproj.EduNest.entity.mentorship;


import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.certificate.Certificate;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@SuperBuilder
@Setter @Getter @NoArgsConstructor @AllArgsConstructor
@Table(name = "mentorship")
public class MentorShip extends BaseEntity {

    private String title;

    private String subtitle;

    private String description;

    private String category; // backEnd,frontEnd

    private Double rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    @Builder.Default
    private DifficultyLevel difficultyLevel = DifficultyLevel.ALL_LEVEL;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.DRAFT;

    private Double price;

    @Column(name = "discount_percentage")
    @Builder.Default
    private Integer discountPercentage = 0;

    @OneToMany(
            mappedBy = "mentorShip",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<WhatWillLearn> whatWillLearn = new ArrayList<>();

    @Column(name = "cover_image_url")
    private String coverImageUrl = "";

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "mentorShip",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Tags> tags = new ArrayList<>();

    private double duration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id",nullable = true)
    private Mentor mentor;

    @OneToMany(
            mappedBy = "mentorShip",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Enrollment> enrollments = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(
            mappedBy = "mentorShip",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<MentorShipReviews> reviews = new ArrayList<>();


    @OneToMany(mappedBy = "mentorship", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Week> weeks = new ArrayList<>();

    @OneToMany(
            mappedBy = "mentorship",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ChatRoom> chatRooms = new ArrayList<>();

    @OneToMany(
            mappedBy = "mentorship",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<TotalPoints> totalPoints = new ArrayList<>();

    @OneToMany(
            mappedBy = "mentorship",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Badge> badges = new ArrayList<>();

    @OneToMany(
            mappedBy = "mentorship",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Certificate> certificates = new ArrayList<>();

    //ToDo StartDate

}
