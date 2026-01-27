package com.example.gradproj.EduNest.repository.quizrepository;

import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    @Query("""
                SELECT q FROM Quiz q
                WHERE (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
                  AND (:status IS NULL OR q.status = :status)
            """)
    Page<Quiz> findQuizzes(
            @Param("quizName") String quizName,
            @Param("status") QuizStatus status,
            Pageable pageable
    );

    List<Quiz> findByMentorship_Id(Long mentorshipId);
}
