package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.UpcomingSessionResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.enums.livesession.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.example.gradproj.EduNest.repository.livesession.projections.MonthlySessionsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<Session,Long> {

    Optional<Session> findByPublicId(UUID publicId);
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT s.id FROM Session s WHERE s.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.publicId = :publicId")
    Optional<Session> findForUpdateByPublicId(@Param("publicId") UUID publicId);

    @Query("""
    select new com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse(
        s.publicId,
        s.title,
        s.scheduledAt,
        w.title,
        m.title
    )
    from Session s
    join s.week w
    join w.mentorship m
    where m.publicId = :mentorshipPublicId
""")
    Page<DashboardSessionResponse> findAllByMentorshipPublicId(
            @Param("mentorshipPublicId") UUID mentorshipPublicId,
            Pageable pageable
    );


    /** EB-51 — L2 session gate lock: ordered row locking (L0 mentorship -> L1 week -> L2 session). */
    /** EB-51 — L2 session gate lock: ordered row locking (L0 mentorship -> L1 week -> L2 session). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findForUpdateById(@Param("id") Long id);

    /** EB-51 — Scalar query to read authoritative database status directly, bypassing 1st-level cache. */
    @Query("SELECT s.status FROM Session s WHERE s.id = :id")
    SessionStatus findStatusById(@Param("id") Long id);

    List<Session> findByStatusAndActualStartTimeBefore(SessionStatus status, LocalDateTime cutoff);

    List<Session> findByStatusAndScheduledAtBefore(SessionStatus status, LocalDateTime cutoff);
    List<Session> findByWeek_Id(Long weekId);

    List<Session> findByWeek_IdIn(List<Long> weekIds);
    boolean existsByWeek_IdAndStatus(Long weekId, SessionStatus status);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.livesession.response.UpcomingSessionResponse(
        s.publicId,
        s.title,
        s.scheduledAt,
        w.publicId,
        w.title,
        m.publicId,
        m.title
    )
    FROM Session s
    JOIN s.week w
    JOIN w.mentorship m
    WHERE s.week.mentorship.mentor.email = :email
      AND s.scheduledAt > :now
    ORDER BY s.scheduledAt ASC
""")
    Page<UpcomingSessionResponse> findUpcomingSessionsByMentorEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );


    @Query("""
    select new com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse(
        s.publicId,
        s.title,
        s.scheduledAt,
        w.title,
        m.title
    )
    from Session s
    join s.week w
    join w.mentorship m
    where m.id = :mentorshipId
""")
    Page<DashboardSessionResponse> findAllByMentorshipId(
            @Param("mentorshipId") Long mentorshipId,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse(
        s.publicId,
        s.title,
        CONCAT(mentor.firstName, ' ', mentor.lastName),
        m.publicId,
        m.title,
        w.publicId,
        w.title,
        s.scheduledAt,
        s.meetingUrl
    )
    FROM Session s
    JOIN s.week w
    JOIN w.mentorship m
    JOIN m.mentor mentor
    WHERE s.scheduledAt > :now
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.id = :studentId
      )
    ORDER BY s.scheduledAt ASC
""")
    Page<StudentUpcomingSessionResponse> findUpcomingSessionsByStudentId(
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse(
        s.publicId,
        s.title,
        CONCAT(mentor.firstName, ' ', mentor.lastName),
        m.publicId,
        m.title,
        w.publicId,
        w.title,
        s.scheduledAt,
        s.meetingUrl
    )
    FROM Session s
    JOIN s.week w
    JOIN w.mentorship m
    JOIN m.mentor mentor
    WHERE s.scheduledAt > :now
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.email = :email
      )
    ORDER BY s.scheduledAt ASC
""")
    Page<StudentUpcomingSessionResponse> findUpcomingSessionsByStudentEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse(
        s.publicId,
        s.title,
        CONCAT(mentor.firstName, ' ', mentor.lastName),
        m.publicId,
        m.title,
        w.publicId,
        w.title,
        s.scheduledAt,
        s.meetingUrl
    )
    FROM Session s
    JOIN s.week w
    JOIN w.mentorship m
    JOIN m.mentor mentor
    WHERE s.scheduledAt > :now
      AND m.id = :mentorshipId
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.email = :email
      )
    ORDER BY s.scheduledAt ASC
""")
    List<StudentUpcomingSessionResponse> findUpcomingSessionsByStudentEmailAndMentorship(
            @Param("email") String email,
            @Param("mentorshipId") Long mentorshipId,
            @Param("now") LocalDateTime now
    );

    @Query("""
    SELECT
        YEAR(s.scheduledAt) as year,
        MONTH(s.scheduledAt) as month,
        COUNT(s.id) as totalSessions
    FROM Session s
    WHERE (:startDate IS NULL OR s.scheduledAt BETWEEN :startDate AND :endDate)
      AND s.status = 'ENDED'
    GROUP BY YEAR(s.scheduledAt), MONTH(s.scheduledAt)
    ORDER BY YEAR(s.scheduledAt), MONTH(s.scheduledAt)
""")
    List<MonthlySessionsProjection> getMonthlySessionsForLastPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

}
