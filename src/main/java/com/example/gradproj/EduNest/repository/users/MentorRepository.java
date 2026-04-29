package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.repository.users.projection.BadgeProjection;
import com.example.gradproj.EduNest.repository.users.projection.MentorStatsProjection;
import com.example.gradproj.EduNest.repository.users.projection.TopMentorProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MentorRepository extends JpaRepository<Mentor, Long> {
    boolean existsByEmail(String email);
    Optional<Mentor> findByEmail(String email);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileforStudentDto(
        m.profileImageUrl,
        m.firstName,
        m.lastName,
        m.yearsOfExperience,
        COUNT(DISTINCT e.id),
        COUNT(DISTINCT r.id),
        AVG(r.rating),
        m.bio,
        m.email,
        null
    )
    FROM Mentor m
    LEFT JOIN m.mentorships ms
    LEFT JOIN ms.enrollments e
    LEFT JOIN ms.reviews r
    WHERE m.email = :email
    GROUP BY m.id, m.profileImageUrl, m.firstName, m.lastName, m.bio, m.email
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
            COUNT(DISTINCT b.id) as totalBadges,
            COUNT(DISTINCT ms.id) as mentorshipCount
        FROM Mentor m
        LEFT JOIN m.mentorships ms
        LEFT JOIN ms.weeks w
        LEFT JOIN w.liveSessions ls
        LEFT JOIN ms.enrollments e
        LEFT JOIN ms.badges b
        WHERE m.id = :mentorId
        """)
    MentorStatsProjection getMentorStats(@Param("mentorId") Long mentorId);

    @Query("""
        SELECT
            b.id as id,
            b.title as title,
            b.category as category,
            b.points as points
        FROM Mentor m
        JOIN m.mentorships ms
        JOIN ms.badges b
        WHERE m.id = :mentorId
        """)
    List<BadgeProjection> getMentorBadges(@Param("mentorId") Long mentorId);

    @Query("SELECT m FROM Mentor m LEFT JOIN FETCH m.socialMediaLinks WHERE m.id = :id")
    Optional<Mentor> findMentorWithSocialMediaById(@Param("id") Long id);
}
