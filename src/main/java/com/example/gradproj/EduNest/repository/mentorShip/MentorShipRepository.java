package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorMentorshipProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorShipListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipStatsResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.RecommendedMentorshipProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorShipRepository extends JpaRepository<MentorShip, Long> {
    boolean existsById(Long id);
    long countByMentor_Id(Long mentorId);

    @EntityGraph(attributePaths = {"tags"})
    Page<MentorShip> findAll(Pageable pageable);

    @Query("""
    SELECT 
        m.id AS id,
        m.title AS title,
        m.rating AS rating,
        COUNT(e.id) AS totalEnroll,
        COALESCE(SUM(e.price),0) AS revenue,
        m.createdAt AS createdDate,
        m.difficultyLevel AS difficultyLevel
    FROM MentorShip m
    LEFT JOIN m.enrollments e
    WHERE m.mentor.email = :email
    GROUP BY m.id
""")
    Page<MentorShipListResponse> findMentorMentorships(
            @Param("email") String email,
            Pageable pageable
    );

    @Query("""
    SELECT 
        m.title AS title,
        m.status AS status,
        COUNT(DISTINCT lec.id) AS totalLessons,
        COUNT(DISTINCT q.id) AS totalQuizzes,
        COUNT(DISTINCT t.id) AS totalAssignments,
        COUNT(DISTINCT s.id) AS totalSessions
    FROM MentorShip m
    LEFT JOIN m.weeks w
    LEFT JOIN w.lectures lec
    LEFT JOIN w.quizzes q
    LEFT JOIN w.tasks t
    LEFT JOIN w.liveSessions s
    WHERE m.id = :mentorshipId
      AND m.mentor.email = :email
    GROUP BY m.title, m.status
""")
    MentorshipStatsResponse getMentorshipStats(
            @Param("mentorshipId") Long mentorshipId,
            @Param("email") String email
    );

    @Query("""
    SELECT m.id AS id, m.title AS name, m.coverImageUrl AS coverImageUrl
    FROM MentorShip m
    WHERE m.mentor.email = :email
""")
    List<MentorMentorshipProjection> findMentorMentorshipsForChatRoom(@Param("email") String email);

    @Query(value = """
    SELECT DISTINCT m.category
    FROM mentorship m
    JOIN enrollments e ON e.mentorship_id = m.id
    JOIN students s ON s.student_id = e.student_id
    JOIN users u ON u.id = s.student_id
    WHERE u.email = :email
    """, nativeQuery = true)
    List<String> findCategoriesByStudentEmail(@Param("email") String email);

    @Query(value = """
    SELECT 
        m.id AS id,
        m.title AS title,
        m.subtitle AS subtitle,
        m.description AS description,
        m.difficulty_level AS difficultyLevel,
        m.duration AS duration,
        m.price AS price,
        m.discount_percentage AS discountPercentage,
        m.cover_image_url AS coverImageUrl,
        CONCAT(u.first_name, ' ', u.last_name) AS mentorName,
        u.email AS mentorEmail
    FROM mentorship m
    JOIN mentors mentor ON m.mentor_id = mentor.mentor_id
    JOIN users u ON u.id = mentor.mentor_id
    LEFT JOIN enrollments e ON e.mentorship_id = m.id
    WHERE m.status = 'ACTIVE'
      AND u.deleted = false
      AND m.id NOT IN (
          SELECT e2.mentorship_id 
          FROM enrollments e2 
          JOIN students s ON s.student_id = e2.student_id
          JOIN users u2 ON u2.id = s.student_id
          WHERE u2.email = :studentEmail
      )
    GROUP BY m.id, m.title, m.subtitle, m.description, m.difficulty_level, 
             m.duration, m.price, m.discount_percentage, m.cover_image_url,
             u.first_name, u.last_name, u.email, m.category, m.rating
    ORDER BY (
        CASE 
            WHEN m.category IN (:categories) THEN 100
            ELSE 0
        END +
        (COALESCE(m.rating, 0) * 15) +
        (COUNT(DISTINCT e.id) * 0.5)
    ) DESC
    LIMIT 5
    """, nativeQuery = true)
    List<RecommendedMentorshipProjection> findRecommendedMentorships(
            @Param("studentEmail") String studentEmail,
            @Param("categories") List<String> categories
    );

}
