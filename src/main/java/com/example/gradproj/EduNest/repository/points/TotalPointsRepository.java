package com.example.gradproj.EduNest.repository.points;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.repository.points.projection.TopStudentResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TotalPointsRepository extends JpaRepository<TotalPoints,Long> {
    Optional<TotalPoints> findByStudent_IdAndMentorship_Id(Long studentId, Long mentorshipId);

    boolean existsByStudent_IdAndMentorship_Id(Long studentId, Long mentorshipId);
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



}
