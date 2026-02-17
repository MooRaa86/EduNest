package com.example.gradproj.EduNest.repository.livesession;

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
    SELECT s
    FROM Session s
    WHERE s.week.mentorship.mentor.email = :email
      AND s.scheduledAt > :now
""")
    Page<Session> findUpcomingSessionsByMentorEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );


}
