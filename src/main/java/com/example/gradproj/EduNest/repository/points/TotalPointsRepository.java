package com.example.gradproj.EduNest.repository.points;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipStudentRankDto;
import com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.repository.points.projection.TopStudentResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TotalPointsRepository extends JpaRepository<TotalPoints,Long> {
    Optional<TotalPoints> findByStudent_IdAndMentorship_Id(Long studentId, Long mentorshipId);
    
    List<TotalPoints> findByMentorship_Id(Long mentorshipId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select tp from TotalPoints tp
        where tp.student.id = :studentId
          and tp.mentorship.id = :mentorshipId
    """)
    Optional<TotalPoints> findForUpdate(Long studentId, Long mentorshipId);

    @Query("""
    SELECT 
        tp.student.id AS studentId,
        tp.student.firstName AS firstName,
        tp.student.lastName AS lastName,
        tp.totalPoints AS totalPoints
    FROM TotalPoints tp
    WHERE tp.mentorship.id = :mentorshipId
    ORDER BY tp.totalPoints DESC
""")
    Page<TopStudentResponse> findTopStudentsByMentorship(
            @Param("mentorshipId") Long mentorshipId,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipStudentRankDto(
        s.id,
        CONCAT(s.firstName, ' ', s.lastName),
        s.email,
        tp.totalPoints,
        CAST(ROW_NUMBER() OVER (ORDER BY tp.totalPoints DESC) AS int)
    )
    FROM TotalPoints tp
    JOIN tp.student s
    WHERE tp.mentorship.id = :mentorshipId
    ORDER BY tp.totalPoints DESC
    """)
    Page<MentorshipStudentRankDto> findStudentsWithRanksByMentorshipId(@Param("mentorshipId") Long mentorshipId, Pageable pageable);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto(
        CONCAT(s.firstName, ' ', s.lastName),
        s.email,
        s.educationalLevel,
        s.profileImageUrl,
        tp.totalPoints,
        0L,
        b.title,
        b.category
    )
    FROM TotalPoints tp
    JOIN tp.student s
    LEFT JOIN BadgeAward ba
        ON ba.student.id = s.id
        AND ba.createdAt = (
            SELECT MAX(ba2.createdAt)
            FROM BadgeAward ba2
            JOIN ba2.badge b2
            WHERE ba2.student.id = s.id
            AND b2.mentorship.id = :mentorshipId
        )
    LEFT JOIN ba.badge b
    WHERE tp.mentorship.id = :mentorshipId
    ORDER BY tp.totalPoints DESC, s.id ASC
""")
    Page<StudentInLeaderboardDto> findLeaderboardByMentorshipId(
            @Param("mentorshipId") Long mentorshipId,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto(
        CONCAT(s.firstName, ' ', s.lastName),
        s.email,
        s.educationalLevel,
        s.profileImageUrl,
        tp.totalPoints,
        (
            SELECT COUNT(tp2) + 1
            FROM TotalPoints tp2
            JOIN tp2.student s2
            WHERE tp2.mentorship.id = :mentorshipId
            AND (
                tp2.totalPoints > tp.totalPoints OR
                (tp2.totalPoints = tp.totalPoints AND s2.id < s.id)
            )
        ),
        b.title,
        b.category
    )
    FROM TotalPoints tp
    JOIN tp.student s
    LEFT JOIN BadgeAward ba
        ON ba.student.id = s.id
    LEFT JOIN ba.badge b 
        ON b.mentorship.id = :mentorshipId
    WHERE tp.mentorship.id = :mentorshipId
    AND s.email = :studentEmail
    AND (
        ba.id IS NULL OR
        ba.createdAt = (
            SELECT MAX(ba2.createdAt)
            FROM BadgeAward ba2
            JOIN ba2.badge b2
            WHERE ba2.student.id = s.id
            AND b2.mentorship.id = :mentorshipId
        )
    )
    """)
    Optional<StudentInLeaderboardDto> findStudentInLeaderboard(
            @Param("mentorshipId") Long mentorshipId,
            @Param("studentEmail") String studentEmail
    );

    /**
     * EB-51 / GAME-04 — atomic single-statement ledger increment for an existing
     * row. The clamp is expressed as a portable CASE expression (identical
     * semantics to SQL {@code GREATEST}) so the identical JPQL compiles to
     * PostgreSQL in production and to H2 MySQL-mode in the no-Docker test
     * profile without a behavior fork. Zero affected rows means the row is
     * absent; the caller then runs the one-shot upsert primitive below.
     *
     * Locking note: this UPDATE takes its own row X-lock; no caller may hold a
     * managed TotalPoints entity in the same persistence context alongside the
     * order of the shared lock hierarchy (L0 mentorship → L4 total_points) that
     * guarantees no cyclic waits.
     */
    @Modifying
    @Query("""
        UPDATE TotalPoints tp
        SET tp.totalPoints =
              CASE WHEN tp.totalPoints + :delta < 0 THEN 0 ELSE tp.totalPoints + :delta END,
            tp.updatedAt = CURRENT_TIMESTAMP,
            tp.updatedBy = :actorEmail
        WHERE tp.student.id = :studentId
          AND tp.mentorship.id = :mentorshipId
    """)
    int incrementIfPresent(@Param("studentId") Long studentId,
                           @Param("mentorshipId") Long mentorshipId,
                           @Param("delta") int delta,
                           @Param("actorEmail") String actorEmail);

    /**
     * EB-51 / GAME-03 + CERT-04 — bulk badge-diff ledger correction, clamped at
     * zero and executed as one statement so its row locks are taken in
     * PostgreSQL's deterministic scan order (no per-row Java loop, so no
     * interleaved lock acquisition). Unclamped semantics of the previous JPQL
     * are retired on purpose.
     */
    @Modifying
    @Query("""
        UPDATE TotalPoints tp
        SET tp.totalPoints =
              CASE WHEN tp.totalPoints + :delta < 0 THEN 0 ELSE tp.totalPoints + :delta END,
            tp.updatedAt = CURRENT_TIMESTAMP,
            tp.updatedBy = :actorEmail
        WHERE tp.student.id IN (
            SELECT ba.student.id
            FROM BadgeAward ba
            WHERE ba.badge.id = :badgeId
        )
        AND tp.mentorship.id = :mentorshipId
    """)
    int applyBadgePointDiff(@Param("badgeId") Long badgeId,
                            @Param("mentorshipId") Long mentorshipId,
                            @Param("delta") int delta,
                            @Param("actorEmail") String actorEmail);

    /**
     * EB-51 — the one-shot insert-or-increment primitive for the row-absent
     * edge case, validated statement-for-statement against PostgreSQL 17.11
     * (T1..T6: zero-delta / negative-delta first insert -> 0, normal arithmetic,
     * below-zero clamp on the conflict branch, concurrent uncommitted-insert
     * race takes the DO UPDATE branch with committed values, single row per
     * (student, mentorship)). PostgreSQLOnly: see TotalPointsServiceImp's
     * dialect gate. Never raises a 23505, so the surrounding Spring transaction
     * is never aborted and no constraint-violation retry exists anywhere.
     *
     * @return 1 on the insert branch, 1 on the update branch (0 impossible —
     *         the conflict branch is always taken or the row is inserted)
     */
    @Modifying
    @Query(value = """
        INSERT INTO total_points
            (student_id, mentorship_id, total_points, public_id,
             created_at, updated_at, created_by, updated_by)
        VALUES
            (:studentId, :mentorshipId, GREATEST(0, :delta),
             gen_random_uuid(), now(), now(), :actorEmail, :actorEmail)
        ON CONFLICT (student_id, mentorship_id)
        DO UPDATE SET
            total_points = GREATEST(0, total_points.total_points + :delta),
            updated_at = now(),
            updated_by = EXCLUDED.updated_by
        """, nativeQuery = true)
    int upsertDeltaRowPostgres(@Param("studentId") Long studentId,
                               @Param("mentorshipId") Long mentorshipId,
                               @Param("delta") int delta,
                               @Param("actorEmail") String actorEmail);

}
