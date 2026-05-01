package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.entity.Roles;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Admin extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Roles role;

    private String email;

    private String firstName;

    private String lastName;

    private String password;

    private String profilePicture;

    private String headline;
}
