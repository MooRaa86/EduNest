package com.example.gradproj.EduNest.repository.quiz;

import java.util.UUID;

import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {
    @Query("SELECT e FROM QuizSubmission e WHERE e.publicId = :publicId")
    java.util.Optional<QuizSubmission> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<QuizSubmission> findByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<QuizSubmission> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return java.util.Optional.empty();
    }
    @Query("SELECT qs.id FROM QuizSubmission qs WHERE qs.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(QuizSubmission::getId);
        }
        return java.util.Optional.empty();
    }
    boolean existsByPublicId(java.util.UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT qs FROM QuizSubmission qs WHERE qs.publicId = :publicId")
    java.util.Optional<QuizSubmission> findForUpdateByPublicId(@Param("publicId") java.util.UUID publicId);


    boolean existsByStudent_IdAndQuiz_Id(Long studentId, Long quizId);

    /**
     * EB-51 — submission-row gate lock (L3): serialises the DOUBLE-SUBMIT race
     * window where two submits for the same (student, quiz) both pass the
     * {@code score != null} recomputation-free eligibility check; both award
     * paths now see the merged state of the first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT qs FROM QuizSubmission qs WHERE qs.student.id = :studentId AND qs.quiz.id = :quizId")
    Optional<QuizSubmission> findByStudentIdAndQuizIdForUpdate(@Param("studentId") Long studentId,
                                                               @Param("quizId") Long quizId);

    /** EB-51 — freeze predicate: at least one scored quiz attempt exists. */
    boolean existsByQuiz_IdAndScoreIsNotNull(Long quizId);
    boolean existsByQuiz_Id(Long quizId);
    /** EB-51 — cascade-delete protection: rejects deleting week if scored quiz attempts exist. */
    boolean existsByQuiz_Week_IdAndScoreIsNotNull(Long weekId);
    boolean existsByQuiz_Week_Id(Long weekId);

    Page<QuizSubmission> findAllByQuiz_Id(Long quizId, Pageable pageable);

    Page<QuizSubmission> findAllByStudent_Id(Long studentId, Pageable pageable);

    @Query("""
        SELECT qs
        FROM QuizSubmission qs
        JOIN qs.quiz q
        WHERE qs.student.id = :studentId
          AND q.week.mentorship.mentor.id = :mentorId
    """)
    Page<QuizSubmission> findAllByStudentAndMentor(
            @Param("studentId") Long studentId,
            @Param("mentorId") Long mentorId,
            Pageable pageable);

    Optional<QuizSubmission> findByStudent_IdAndQuiz_Id(Long studentId, Long quizId);

    @Query("""
        SELECT qs
        FROM QuizSubmission qs
        JOIN qs.quiz q
        WHERE qs.student.id = :studentId
          AND q.week.id = :weekId
    """)
    List<QuizSubmission> findByStudentIdAndWeekId(@Param("studentId") Long studentId,
                                                 @Param("weekId") Long weekId);

    @Query("""
        SELECT qs
        FROM QuizSubmission qs
        JOIN qs.quiz q
        WHERE qs.student.id = :studentId
          AND q.week.id IN :weekIds
    """)
    List<QuizSubmission> findByStudent_IdAndQuiz_Week_IdIn(@Param("studentId") Long studentId,
                                                            @Param("weekIds") List<Long> weekIds);


    @Query("""
        SELECT qs
        FROM QuizSubmission qs
        JOIN qs.quiz q
        JOIN q.week w
        WHERE qs.student.id = :studentId
          AND w.mentorship.id = :mentorshipId
    """)
    List<QuizSubmission> findByStudentIdAndMentorshipId(@Param("studentId") Long studentId,
                                                        @Param("mentorshipId") Long mentorshipId);
}
