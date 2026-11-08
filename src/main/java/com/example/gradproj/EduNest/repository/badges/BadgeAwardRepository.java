package com.example.gradproj.EduNest.repository.badges;

import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.users.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
        SELECT DISTINCT ba.student
        FROM BadgeAward ba
        WHERE ba.badge.id = :badgeId
        ORDER BY ba.student.id ASC
    """)
    List<Student> findDistinctStudentsByBadgeId(@Param("badgeId") Long badgeId);

    List<BadgeAward> findByStudent_IdAndBadge_Mentorship_Id(Long studentId, Long mentorshipId);
}
