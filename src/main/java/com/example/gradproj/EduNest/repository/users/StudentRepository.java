package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Student findByEmail(String email);
    boolean existsByEmail(String email);
}
