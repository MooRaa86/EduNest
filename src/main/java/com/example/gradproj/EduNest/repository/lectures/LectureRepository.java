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
    Optional<Lecture> findByPublicId(UUID publicId);

    @Query("SELECT l.id FROM Lecture l WHERE l.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);
    List<Lecture> findByWeek_Id(Long weekId);

    List<Lecture> findByWeek_IdIn(List<Long> weekIds);

    Page<Lecture> findByWeek_Mentorship_Id(Long mentorshipId, Pageable pageable);
}
