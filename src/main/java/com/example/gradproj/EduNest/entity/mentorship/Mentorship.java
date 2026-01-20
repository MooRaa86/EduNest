package com.example.gradproj.EduNest.entity.mentorship;


import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.Mentor;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.enums.DifficultyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@SuperBuilder
@Setter @Getter @NoArgsConstructor @AllArgsConstructor
@Table(name = "mentorship")
public class Mentorship extends BaseEntity {

    private String title;

    private String description;

    private String category;

    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id",nullable = true)
    private Mentor mentor;

    @ManyToMany
    @JoinTable(
            name = "enrollments",
            joinColumns = @JoinColumn(name = "mentorship_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    private Set<Student> students = new HashSet<>();

}