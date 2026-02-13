package com.example.gradproj.EduNest.repository.points;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
}
