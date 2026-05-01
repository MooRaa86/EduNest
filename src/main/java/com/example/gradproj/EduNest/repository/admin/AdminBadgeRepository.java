package com.example.gradproj.EduNest.repository.admin;

import com.example.gradproj.EduNest.entity.admin.AdminBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminBadgeRepository extends JpaRepository<AdminBadge, Long> {
}
