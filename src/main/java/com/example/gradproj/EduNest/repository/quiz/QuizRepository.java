package com.example.gradproj.EduNest.repository.quiz;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    @Query("""
    SELECT q FROM Quiz q
    WHERE q.week.mentorship.id = :mentorshipId
      AND (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND (:status IS NULL OR q.status = :status)
""")
    Page<Quiz> findQuizzesByMentorship(
            @Param("mentorshipId") Long mentorshipId,
            @Param("quizName") String quizName,
            @Param("status") QuizStatus status,
            Pageable pageable
    );
//    List<Quiz> findByMentorship_Id(Long mentorshipId);
    List<Quiz> findByWeek_Mentorship_Id(Long mentorshipId);
    void deleteById(Long id);
    List<Quiz> findByWeek_Id(Long weekId);
    boolean existsById(Long id);
}
