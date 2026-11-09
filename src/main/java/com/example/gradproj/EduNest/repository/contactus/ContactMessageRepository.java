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

    @Query("SELECT e FROM ContactMessageEntity e WHERE e.publicId = :publicId")
    Optional<ContactMessageEntity> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<ContactMessageEntity> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<ContactMessageEntity> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT cm.id FROM ContactMessageEntity cm WHERE cm.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(ContactMessageEntity::getId);
        }
        return Optional.empty();
    }

    Page<ContactMessageEntity> findByStatus(MessageStatus status, Pageable pageable);
}
