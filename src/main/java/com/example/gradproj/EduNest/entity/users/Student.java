package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Reviews;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;

@Entity
@Table(name = "students")
@PrimaryKeyJoinColumn(name = "student_id") // الـ FK اللي بيربط مع جدول users
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Student extends UserEntity {

    // الـ ID بيورثه من الأب (UserEntity)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,name = "educational_level")
    private EducationalLevel educationalLevel;

    @OneToMany(
            mappedBy = "student",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Enrollment> enrollments = new ArrayList<>();


    @OneToMany(
            mappedBy = "student",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Reviews> reviews = new ArrayList<>();

}