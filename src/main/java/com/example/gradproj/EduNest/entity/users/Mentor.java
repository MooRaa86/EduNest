package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

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
    
    @Column(name = "years_of_experience")
    private double yearsOfExperience;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @JsonIgnore
    @OneToMany(mappedBy = "mentor",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<MentorShip> mentorships = new ArrayList<>();

}