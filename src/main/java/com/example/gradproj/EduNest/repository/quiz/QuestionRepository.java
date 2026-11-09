package com.example.gradproj.EduNest.repository.quiz;

import java.util.UUID;

import com.example.gradproj.EduNest.entity.quiz.Question;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query("SELECT e FROM Question e WHERE e.publicId = :publicId")
    java.util.Optional<Question> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<Question> findByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<Question> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return java.util.Optional.empty();
    }
    @Query("SELECT q.id FROM Question q WHERE q.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(Question::getId);
        }
        return java.util.Optional.empty();
    }
    boolean existsByPublicId(java.util.UUID publicId);
    void deleteByPublicId(java.util.UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Question q WHERE q.publicId = :publicId")
    java.util.Optional<Question> findForUpdateByPublicId(@Param("publicId") java.util.UUID publicId);

    List<Question> findByQuiz_Id(Long quizId);
    boolean existsByQuiz_Id(Long quizId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Question q WHERE q.id = :id")
    Optional<Question> findForUpdateById(@Param("id") Long id);
}
