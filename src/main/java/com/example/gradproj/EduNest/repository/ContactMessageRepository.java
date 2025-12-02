package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.ContactMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessageEntity,Long> {
}
