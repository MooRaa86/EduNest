package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.entity.livesession.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

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


    @Query("""
    select new com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse(
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

}
