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
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {

    @Query("SELECT e FROM Badge e WHERE e.publicId = :publicId")
    Optional<Badge> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Badge> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Badge> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT b.id FROM Badge b WHERE b.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(Badge::getId);
        }
        return Optional.empty();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Badge b WHERE b.publicId = :publicId")
    Optional<Badge> findForUpdateByPublicId(@Param("publicId") UUID publicId);

    List<Badge> findByMentorship_PublicId(UUID mentorshipPublicId);
    int countByMentorship_PublicId(UUID mentorshipPublicId);

    List<Badge> findByMentorship_Id(Long mentorshipId);
    int countByMentorship_Id(Long mentorshipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Badge b WHERE b.id = :id")
    Optional<Badge> findForUpdateById(@Param("id") Long id);
}
