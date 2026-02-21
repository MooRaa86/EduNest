package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
public class SocialMedia extends BaseEntity {
    @Column(name = "facebook_url")
    private String facebook;
    @Column(name = "github_url")
    private String github;
    @Column(name = "linked_in_url")
    private String linkedin;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;
}
