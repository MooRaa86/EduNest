package com.example.gradproj.EduNest.repository.contactus;

import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessageEntity,Long> {
}
