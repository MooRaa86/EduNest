package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);
    Optional<Admin> findByPublicId(UUID publicId);
    void deleteByEmail(String email);
    Optional<Admin> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Admin> findTopByOrderByCreatedAtDesc();
}
