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
import java.util.UUID;
import java.util.Optional;

@Repository
public interface BadgeAwardRepository extends JpaRepository<BadgeAward, Long> {

    @Query("SELECT e FROM BadgeAward e WHERE e.publicId = :publicId")
    Optional<BadgeAward> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<BadgeAward> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<BadgeAward> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT ba.id FROM BadgeAward ba WHERE ba.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(BadgeAward::getId);
        }
        return Optional.empty();
    }

    List<BadgeAward> findByBadge_Mentorship_PublicId(UUID mentorshipPublicId);
    List<BadgeAward> findByStudent_PublicIdOrderByCreatedAtDesc(UUID studentPublicId);
    List<BadgeAward> findByBadge_PublicId(UUID badgePublicId);

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
