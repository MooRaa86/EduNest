package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity,Long> {
    boolean existsByEmail(String email);

}
