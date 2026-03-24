package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.UpcomingSessionResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LiveSessionRepository extends JpaRepository<Session,Long> {
    List<Session> findByWeek_Id(Long weekId);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.livesession.response.UpcomingSessionResponse(
        s.id,
        s.title,
        s.scheduledAt,
        w.id,
        w.title,
        m.id,
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
        s.id,
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
        s.id,
        s.title,
        CONCAT(mentor.firstName, ' ', mentor.lastName),
        m.id,
        m.title,
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
        s.id,
        s.title,
        CONCAT(mentor.firstName, ' ', mentor.lastName),
        m.id,
        m.title,
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

}
