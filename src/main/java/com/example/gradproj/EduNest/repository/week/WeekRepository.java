package com.example.gradproj.EduNest.repository.week;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeekRepository extends JpaRepository<Week,Long> {
    void deleteById(Long id);
    List<Week> findByMentorship_IdOrderByIdAsc(Long mentorshipId);

    /** EB-51 — shared gate lock (L1) for task/project points-edit and grading gates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Week w WHERE w.id = :id")
    Optional<Week> findForUpdateById(@Param("id") Long id);

    @Query("""
    SELECT COUNT(DISTINCT w.id)
    FROM Week w
    WHERE w.mentorship.id = :mentorshipId
      AND NOT EXISTS (
        SELECT 1 FROM Task t
        WHERE t.week.id = w.id
          AND t.status = 'PUBLISHED'
          AND NOT EXISTS (
            SELECT 1 FROM TaskSubmission ts
            WHERE ts.task.id = t.id
              AND ts.student.email = :studentEmail
          )
      )
      AND NOT EXISTS (
        SELECT 1 FROM Project p
        WHERE p.week.id = w.id
          AND p.status = 'PUBLISHED'
          AND NOT EXISTS (
            SELECT 1 FROM ProjectSubmission ps
            WHERE ps.project.id = p.id
              AND ps.student.email = :studentEmail
          )
      )
      AND NOT EXISTS (
        SELECT 1 FROM Quiz q
        WHERE q.week.id = w.id
          AND q.status = 'PUBLISHED'
          AND NOT EXISTS (
            SELECT 1 FROM QuizSubmission qs
            WHERE qs.quiz.id = q.id
              AND qs.student.email = :studentEmail
          )
      )
    """)
    Long countCompletedWeeksByStudentEmail(
            @Param("mentorshipId") Long mentorshipId,
            @Param("studentEmail") String studentEmail
    );

}
