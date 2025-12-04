package com.example.gradproj.EduNest.entity;

import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
//@Table(name = "user")
//@Builder
@MappedSuperclass
public class UserEntity extends BaseEntity {

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phoneNumber;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", referencedColumnName = "roleId",nullable = false)
    private Roles roles;

}
