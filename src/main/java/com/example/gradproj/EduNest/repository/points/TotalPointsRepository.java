package com.example.gradproj.EduNest.repository.points;

import com.example.gradproj.EduNest.entity.points.TotalPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TotalPointsRepository extends JpaRepository<TotalPoints,Long> {
    Optional<TotalPoints> findByStudent_IdAndMentorship_Id(Long studentId, Long mentorshipId);

    boolean existsByStudent_IdAndMentorship_Id(Long studentId, Long mentorshipId);
}
