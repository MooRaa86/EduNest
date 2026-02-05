package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndEnabledTrue(String email);
}
