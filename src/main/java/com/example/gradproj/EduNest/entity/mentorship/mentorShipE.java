package com.example.gradproj.EduNest.entity.mentorship;


import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@SuperBuilder
@Setter @Getter @NoArgsConstructor @AllArgsConstructor
@Table(name = "mentorship")
public class mentorShipE extends BaseEntity {

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


    @OneToMany(mappedBy = "mentorship", fetch = FetchType.LAZY)
    private List<Task> tasks = new ArrayList<>();
}
