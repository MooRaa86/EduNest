package com.example.gradproj.EduNest.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "mentor")
public class Mentor extends UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String jobTitle; // enum -> static
    private String bio;
    private String linkedInUrl;
    private String githubUrl;
    private double yearsOfExperience;
}
