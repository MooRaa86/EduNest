package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileReviews;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipReviewProjection;
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

    @Query("""
    SELECT 
        r.id as reviewId,
        r.feedBack as feedback,
        r.rating as rating,
        CONCAT(s.firstName, ' ', s.lastName) as studentFullName,
        s.profileImageUrl as studentProfileImageUrl,
        s.email as studentEmail
    FROM MentorShipReviews r
    JOIN r.student s
    WHERE r.mentorShip.id = :mentorshipId
    ORDER BY 
        CASE 
            WHEN :studentEmail IS NOT NULL AND s.email = :studentEmail THEN 0 
            ELSE 1 
        END,
        r.rating DESC,
        r.createdAt DESC
""")
    Page<MentorshipReviewProjection> findMentorshipReviews(
            @Param("mentorshipId") Long mentorshipId,
            @Param("studentEmail") String studentEmail,
            Pageable pageable
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileReviews(
        r.id,
        CONCAT(r.student.firstName, ' ', r.student.lastName),
        r.student.email,
        r.rating,
        r.feedBack,
        r.student.profileImageUrl,
        r.mentorShip.id,
        r.mentorShip.title,
        r.createdAt
    )
    FROM MentorShipReviews r
    WHERE r.mentorShip.mentor.email = :email
    ORDER BY r.rating DESC
    """)
    Page<MentorProfileReviews> findReviewsByMentorEmail(
            @Param("email") String email,
            Pageable pageable
    );

}
