package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
    Optional<Mentor> findByEmail(String email);
}
