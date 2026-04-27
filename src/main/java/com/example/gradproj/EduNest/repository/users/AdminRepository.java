package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    void deleteByEmail(String email);
}
