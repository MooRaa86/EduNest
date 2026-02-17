package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewsRepository extends JpaRepository<MentorShipReviews, Long> {

    List<MentorShipReviews> findByMentorShipId(Long mentorShipId);
    MentorShipReviews findByMentorShipIdAndStudentId(Long mentorShipId, Long studentId);

    Page<MentorShipReviews> findByMentorShip_Mentor_Email(
            String email,
            Pageable pageable
    );

    Page<MentorShipReviews> findByMentorShip_Id(Long mentorShipId, Pageable pageable);


    @Query("""
    SELECT COALESCE(AVG(r.rating), 0)
    FROM MentorShipReviews r
    WHERE r.mentorShip.mentor.id = :mentorId
""")
    Double findAverageRatingByMentorId(@Param("mentorId") Long mentorId);

    @Query("""
    SELECT AVG(r.rating)
    FROM MentorShipReviews r
    WHERE r.mentorShip.id = :mentorshipId
""")
    Double calculateAverageRating(@Param("mentorshipId") Long mentorshipId);

}
