package com.example.gradproj.EduNest.repository.badges;

import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeAwardRepository extends JpaRepository<BadgeAward, Long> {
    boolean existsByBadge_IdAndStudent_Id(Long badgeId, Long studentId);
    boolean existsByBadge_Id(Long badgeId);
    List<BadgeAward> findByStudent_IdOrderByCreatedAtDesc(Long studentId);
    Page<BadgeAward> findByStudent_IdOrderByCreatedAtDesc(Long studentId, Pageable pageable);
    List<BadgeAward> findByBadge_Id(Long badgeId);
    List<BadgeAward> findByBadge_Mentorship_Id(Long mentorshipId);
}
