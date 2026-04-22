package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmail(String email);
    @Query("select s.id from Student s where s.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);
    List<Student> findAllByEmailIn(Collection<String> emails);
}
