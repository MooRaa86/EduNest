package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;

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
    @Column(name = "job_title")
    private String jobTitle;

    @Column(length = 500,name = "bio")
    private String bio;

    @Column(name = "linked_in_url")
    private String linkedInUrl;
    @Column(name = "github_url")
    private String githubUrl;
    @Column(name = "years_of_experience")
    private double yearsOfExperience;

    @OneToMany(mappedBy = "mentor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<mentorShipE> mentorships = new ArrayList<>();

}