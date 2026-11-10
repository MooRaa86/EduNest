package com.example.gradproj.EduNest.repository.contactus;

import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.message.MessageStatus;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessageEntity,Long> {

    Optional<ContactMessageEntity> findByPublicId(UUID publicId);
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT cm.id FROM ContactMessageEntity cm WHERE cm.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    Page<ContactMessageEntity> findByStatus(MessageStatus status, Pageable pageable);
}
