package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.repository.users.projection.MentorStatsProjection;
import com.example.gradproj.EduNest.repository.users.projection.TopMentorProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
    Optional<Mentor> findByEmail(String email);

    boolean existsByPublicId(UUID publicId);
    Optional<Mentor> findByPublicId(UUID publicId);

    @Query("select m.id from Mentor m where m.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    @Query("SELECT m FROM Mentor m LEFT JOIN FETCH m.socialMediaLinks WHERE m.publicId = :publicId")
    Optional<Mentor> findMentorWithSocialMediaByPublicId(@Param("publicId") UUID publicId);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto(
        m.publicId,
        m.profileImageUrl,
        m.firstName,
        m.lastName,
        m.yearsOfExperience,
        COUNT(DISTINCT e.id),
        COUNT(DISTINCT r.id),
        AVG(r.rating),
        m.bio,
        null,
        null
    )
    FROM Mentor m
    LEFT JOIN m.mentorships ms
    LEFT JOIN ms.enrollments e
    LEFT JOIN ms.reviews r
    WHERE m.publicId = :publicId
    GROUP BY m.publicId, m.profileImageUrl, m.firstName, m.lastName, m.bio, m.yearsOfExperience
    """)
    MentorProfileforStudentDto findMentorProfileByPublicId(@Param("publicId") UUID publicId);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto(
        m.publicId,
        m.profileImageUrl,
        m.firstName,
        m.lastName,
        m.yearsOfExperience,
        COUNT(DISTINCT e.id),
        COUNT(DISTINCT r.id),
        AVG(r.rating),
        m.bio,
        null,
        null
    )
    FROM Mentor m
    LEFT JOIN m.mentorships ms
    LEFT JOIN ms.enrollments e
    LEFT JOIN ms.reviews r
    WHERE m.id = :id
    GROUP BY m.publicId, m.profileImageUrl, m.firstName, m.lastName, m.bio, m.yearsOfExperience
    """)
    MentorProfileforStudentDto findMentorProfileById(@Param("id") Long id);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto(
        m.publicId,
        m.profileImageUrl,
        m.firstName,
        m.lastName,
        m.yearsOfExperience,
        COUNT(DISTINCT e.id),
        COUNT(DISTINCT r.id),
        AVG(r.rating),
        m.bio,
        null,
        null
    )
    FROM Mentor m
    LEFT JOIN m.mentorships ms
    LEFT JOIN ms.enrollments e
    LEFT JOIN ms.reviews r
    WHERE m.email = :email
    GROUP BY m.publicId, m.profileImageUrl, m.firstName, m.lastName, m.bio, m.yearsOfExperience
    """)
    MentorProfileforStudentDto findMentorProfileByEmail(@Param("email") String email);

    @Query("""
        SELECT
            CONCAT(m.firstName, ' ', m.lastName) as fullName,
            m.email as email,
            m.profileImageUrl as profileImageUrl,
            COUNT(DISTINCT e.student.id) as totalStudents,
            COALESCE(SUM(e.price), 0) as totalRevenue
        FROM Mentor m
        LEFT JOIN m.mentorships ms
        LEFT JOIN ms.enrollments e
        GROUP BY m.id, m.firstName, m.lastName, m.email, m.profileImageUrl
        ORDER BY COUNT(DISTINCT e.student.id) DESC, COALESCE(SUM(e.price), 0) DESC
    """)
    Page<TopMentorProjection> findTopMentorsByTotalStudents(Pageable pageable);

    @Query("""
        SELECT
            COUNT(DISTINCT ls.id) as totalSessions,
            COUNT(DISTINCT e.student.id) as totalStudents,
            COALESCE((SELECT AVG(r2.rating) FROM Mentor m2 JOIN m2.mentorships ms2 JOIN ms2.reviews r2 WHERE m2.id = :mentorId), 0.0) as averageRating,
            0 as totalBadges,
            COUNT(DISTINCT ms.id) as mentorshipCount
        FROM Mentor m
        LEFT JOIN m.mentorships ms
        LEFT JOIN ms.weeks w
        LEFT JOIN w.liveSessions ls
        LEFT JOIN ms.enrollments e
        WHERE m.id = :mentorId
        """)
    MentorStatsProjection getMentorStats(@Param("mentorId") Long mentorId);


    @Query("SELECT m FROM Mentor m LEFT JOIN FETCH m.socialMediaLinks WHERE m.id = :id")
    Optional<Mentor> findMentorWithSocialMediaById(@Param("id") Long id);
}
