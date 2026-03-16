package com.example.gradproj.EduNest.repository.badges;

import com.example.gradproj.EduNest.entity.badges.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByMentorship_Id(Long mentorshipId);
    int countByMentorship_Id(Long mentorshipId);
}
