package com.example.gradproj.EduNest.repository.account;

import com.example.gradproj.EduNest.entity.account.Settings;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Settings, Integer> {
    Optional<Settings> findByUserEmail(String email);

    void deleteByUser(UserEntity user);

    Optional<Settings> findByUser(UserEntity user);
}