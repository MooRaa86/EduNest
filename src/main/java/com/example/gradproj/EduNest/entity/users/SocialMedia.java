package com.example.gradproj.EduNest.entity.users;

import com.example.gradproj.EduNest.entity.BaseEntity;
import com.example.gradproj.EduNest.enums.socialMedia.Media;
import jakarta.persistence.*;
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
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Media name;
    
    @Column(nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
}
