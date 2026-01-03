package com.example.gradproj.EduNest.entity;

import com.example.gradproj.EduNest.enums.EducationalLevel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
}