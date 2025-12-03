package com.example.gradproj.EduNest.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Roles extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    private String roleName;
}
