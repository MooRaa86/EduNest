package com.example.gradproj.EduNest.repository.quiz;

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
    java.util.Optional<Question> findByPublicId(java.util.UUID publicId);
    @Query("SELECT q.id FROM Question q WHERE q.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicId(@Param("publicId") java.util.UUID publicId);
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
