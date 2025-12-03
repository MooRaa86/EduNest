package com.example.gradproj.EduNest.entity;

import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user")
@Builder
public class UserEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fristName;
    private String lastName;
    private String email;
    private String password;
    private String phoneNumber;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", referencedColumnName = "roleId",nullable = false)
    private Roles roles;

}
