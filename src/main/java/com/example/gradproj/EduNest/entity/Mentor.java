package com.example.gradproj.EduNest.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "mentors")
@PrimaryKeyJoinColumn(name = "mentor_id") // الـ FK اللي بيربط مع جدول users
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Mentor extends UserEntity {

    // الـ ID بيورثه من الأب

    private String jobTitle;

    @Column(length = 500)
    private String bio;

    private String linkedInUrl;

    private String githubUrl;

    private double yearsOfExperience;
}