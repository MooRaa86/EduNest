package com.example.gradproj.EduNest.repository.quizrepository;

import com.example.gradproj.EduNest.entity.quizEntity.Quiz;
import com.example.gradproj.EduNest.enums.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findById(Long id);


    @Query("""
    SELECT q FROM Quiz q
    WHERE (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND (:status IS NULL OR q.status = :status)
      AND (:deadline IS NULL OR q.deadline = :deadline)
""")
    Page<Quiz> findQuizzes(
            @Param("quizName") String quizName,
            @Param("status") QuizStatus status,
            @Param("deadline") LocalDate deadline,
            Pageable pageable
    );
}
