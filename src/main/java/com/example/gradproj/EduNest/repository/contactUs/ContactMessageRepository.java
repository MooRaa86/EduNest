package com.example.gradproj.EduNest.repository.contactUs;

import com.example.gradproj.EduNest.entity.contactUs.ContactMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessageEntity,Long> {
}
