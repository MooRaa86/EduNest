package com.example.gradproj.EduNest.entity.account;

import com.example.gradproj.EduNest.entity.*;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.account.ThemeMode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_settings")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Data
public class Settings extends BaseEntity {

    private boolean emailNotifications;
    private boolean pushNotifications;
    private boolean weeklyDigest;
    private boolean messageNotifications;

    @Enumerated(EnumType.STRING)
    private ThemeMode mode;

    @OneToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

}