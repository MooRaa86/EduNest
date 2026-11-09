package com.example.gradproj.EduNest.repository.lectures;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    @Query("SELECT e FROM Lecture e WHERE e.publicId = :publicId")
    Optional<Lecture> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Lecture> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Lecture> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }

    @Query("SELECT l.id FROM Lecture l WHERE l.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(Lecture::getId);
        }
        return Optional.empty();
    }

    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);
    List<Lecture> findByWeek_Id(Long weekId);

    List<Lecture> findByWeek_IdIn(List<Long> weekIds);

    Page<Lecture> findByWeek_Mentorship_Id(Long mentorshipId, Pageable pageable);
}
