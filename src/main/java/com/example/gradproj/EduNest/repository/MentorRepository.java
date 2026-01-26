package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
    Mentor findByEmail(String email);
}
