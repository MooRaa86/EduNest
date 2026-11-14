package com.example.gradproj.EduNest.repository.quiz;

import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    java.util.Optional<Quiz> findByPublicId(java.util.UUID publicId);
    @Query("SELECT q.id FROM Quiz q WHERE q.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicId(@Param("publicId") java.util.UUID publicId);
    boolean existsByPublicId(java.util.UUID publicId);
    void deleteByPublicId(java.util.UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quiz q WHERE q.publicId = :publicId")
    java.util.Optional<Quiz> findForUpdateByPublicId(@Param("publicId") java.util.UUID publicId);

    /** EB-51 — shared gate lock (L2-quiz) for the quiz-submit gate ordering. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quiz q WHERE q.id = :id")
    Optional<Quiz> findForUpdateById(@Param("id") Long id);
    /**
     * EB-60 / P4-11 — F2 projection row: aggregates the per-quiz values the
     * listing mappings used to read by navigating the LAZY submissions
     * collection of each entity row. {@code COUNT(s)} mirrors
     * {@code submissions.size()} (all attempts, opened included);
     * {@code AVG(s.score)} ignores NULL scores exactly like
     * {@code calculateAverageScore}; per-quiz rounding stays in the service.
     */
    interface QuizListRow {
        UUID getPublicId();
        String getTitle();
        Integer getDurationMinutes();
        String getDescription();
        QuizStatus getStatus();
        LocalDateTime getCloseAt();
        Long getSubmissionCount();
        Double getAverageScore();
    }

    /**
     * EB-60 / P4-11 — F2: replaces the previous {@code LEFT JOIN FETCH
     * q.submissions} on a bag with a Page result. The fetch join made Hibernate
     * paginate in memory (HHH000104: the LIMIT was never sent to the database
     * and every matching quiz's submissions were loaded); the grouped
     * projection restores database-level pagination and eliminates the
     * per-row submissions navigation. No to-many fetch join on a pageable
     * query.
     */
    @Query(value = """
    SELECT q.publicId as publicId, q.title as title, q.durationMinutes as durationMinutes,
           q.description as description, q.status as status, q.closeAt as closeAt,
           COUNT(s) as submissionCount, AVG(s.score) as averageScore
    FROM Quiz q
    LEFT JOIN q.submissions s
    WHERE q.week.mentorship.id = :mentorshipId
      AND (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND (:status IS NULL OR q.status = :status)
    GROUP BY q.id, q.publicId, q.title, q.durationMinutes, q.description, q.status, q.closeAt
    ORDER BY q.id
    """,
    countQuery = """
    SELECT COUNT(q) FROM Quiz q
    WHERE q.week.mentorship.id = :mentorshipId
      AND (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND (:status IS NULL OR q.status = :status)
    """)
    Page<QuizListRow> findQuizzesByMentorship(
            @Param("mentorshipId") Long mentorshipId,
            @Param("quizName") String quizName,
            @Param("status") QuizStatus status,
            Pageable pageable
    );

    /**
     * EB-60 / P4-11 — F2: student variant of {@link #findQuizzesByMentorship};
     * keeps the DRAFT exclusion of the previous entity query.
     */
    @Query(value = """
    SELECT q.publicId as publicId, q.title as title, q.durationMinutes as durationMinutes,
           q.description as description, q.status as status, q.closeAt as closeAt,
           COUNT(s) as submissionCount, AVG(s.score) as averageScore
    FROM Quiz q
    LEFT JOIN q.submissions s
    WHERE q.week.mentorship.id = :mentorshipId
      AND (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND q.status != 'DRAFT'
      AND (:status IS NULL OR q.status = :status)
    GROUP BY q.id, q.publicId, q.title, q.durationMinutes, q.description, q.status, q.closeAt
    ORDER BY q.id
    """,
    countQuery = """
    SELECT COUNT(q) FROM Quiz q
    WHERE q.week.mentorship.id = :mentorshipId
      AND (:quizName IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', :quizName, '%')))
      AND q.status != 'DRAFT'
      AND (:status IS NULL OR q.status = :status)
    """)
    Page<QuizListRow> findStudentQuizzesByMentorship(
            @Param("mentorshipId") Long mentorshipId,
            @Param("quizName") String quizName,
            @Param("status") QuizStatus status,
            Pageable pageable
    );

    /**
     * EB-60 / P4-11 — F1 projection row: one aggregate row per quiz, so the
     * dashboard can preserve its exact semantics — per-quiz rounded average
     * summed in Java and divided by the total quiz count (all statuses) —
     * without loading every quiz entity and its submissions collection.
     */
    interface QuizAggregateRow {
        QuizStatus getStatus();
        Double getAverageScore();
    }

    @Query("""
    SELECT q.status as status, AVG(s.score) as averageScore
    FROM Quiz q
    LEFT JOIN q.submissions s
    WHERE q.week.mentorship.id = :mentorshipId
    GROUP BY q.id, q.status
    """)
    List<QuizAggregateRow> findDashboardAggregatesByMentorship(@Param("mentorshipId") Long mentorshipId);

    void deleteById(Long id);
    List<Quiz> findByWeek_Id(Long weekId);
    List<Quiz> findByWeek_IdAndStatusNot(Long weekId, QuizStatus status);
    boolean existsById(Long id);
    List<Quiz> findByWeek_IdIn(List<Long> weekIds);

    @Query("SELECT q FROM Quiz q WHERE q.week.id IN :weekIds AND q.status != :status")
    List<Quiz> findByWeek_IdInAndStatusNot(@Param("weekIds") List<Long> weekIds, @Param("status") QuizStatus status);

    /**
     * EB-53 — auto-close candidates: PUBLISHED quizzes whose closeAt deadline has
     * passed, deterministically ordered. Legacy quizzes (closeAt NULL) are never
     * selected — they keep manual-close-only behavior.
     */
    @Query("""
        SELECT q.id FROM Quiz q
        WHERE q.status = :status
          AND q.closeAt IS NOT NULL
          AND q.closeAt <= :now
        ORDER BY q.id
    """)
    List<Long> findIdsDueForAutoClose(@Param("status") QuizStatus status, @Param("now") java.time.LocalDateTime now);
}