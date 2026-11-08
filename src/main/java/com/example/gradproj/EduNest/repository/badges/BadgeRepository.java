package com.example.gradproj.EduNest.repository.badges;

import com.example.gradproj.EduNest.entity.badges.Badge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByMentorship_Id(Long mentorshipId);
    int countByMentorship_Id(Long mentorshipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Badge b WHERE b.id = :id")
    Optional<Badge> findForUpdateById(@Param("id") Long id);
}
