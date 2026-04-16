package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileMentorshipsDto;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.repository.mentorShip.projections.*;
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

    boolean existsByIdAndMentor_Email(Long id, String email);

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
        COUNT(DISTINCT s.id) AS totalSessions,
        COUNT(DISTINCT p.id) AS totalProjects
    FROM MentorShip m
    LEFT JOIN m.weeks w
    LEFT JOIN w.lectures lec
    LEFT JOIN w.quizzes q
    LEFT JOIN w.tasks t
    LEFT JOIN w.liveSessions s
    LEFT JOIN w.projects p
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

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto(
        m.id, m.title, m.subtitle, m.description, m.category,
        CONCAT(m.mentor.firstName, ' ', m.mentor.lastName) as mentorName,
        m.price,
        m.discountPercentage,
        m.price * (1.0 - m.discountPercentage / 100.0),
        m.duration, m.coverImageUrl,m.rating
    )
    FROM MentorShip m
    WHERE m.status = 'ACTIVE'
    AND m.mentor.deleted = false
    AND (:keyword IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(CONCAT(m.mentor.firstName, ' ', m.mentor.lastName)) LIKE LOWER(CONCAT('%', :keyword, '%')))
    AND (:category IS NULL OR LOWER(m.category) = LOWER(:category))
    AND (:minPrice IS NULL OR m.price >= :minPrice)
    AND (:maxPrice IS NULL OR m.price <= :maxPrice)
""")
    Page<MentorshipExploreDto> searchMentorShips(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );

    @Query("""
    SELECT DISTINCT m.category FROM MentorShip m WHERE m.status = 'ACTIVE' AND m.mentor.deleted = false
    """)
    List<String> findAllCategories();

    @Query("""
    SELECT t.tag
    FROM Tags t
    WHERE t.mentorShip.id = :mentorshipId
    """)
    List<String> findTagsByMentorshipId(@Param("mentorshipId") Long mentorshipId);

    @Query("""
    SELECT w.content
    FROM WhatWillLearn w
    WHERE w.mentorShip.id = :mentorshipId
    """)
    List<String> findWhatWillLearnByMentorshipId(@Param("mentorshipId") Long mentorshipId);

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
      AND NOT EXISTS (
        SELECT 1
        FROM enrollments e2
        JOIN students s ON s.student_id = e2.student_id
        JOIN users u2 ON u2.id = s.student_id
        WHERE u2.email = :studentEmail
          AND e2.mentorship_id = m.id
      )
    GROUP BY m.id, m.title, m.subtitle, m.description, m.difficulty_level, 
             m.duration, m.price, m.discount_percentage, m.cover_image_url,
             u.first_name, u.last_name, u.email, m.category, m.rating
    ORDER BY (
        CASE 
            WHEN :hasCategories = true AND LOWER(m.category) IN (:categories) THEN 100
            ELSE 0
        END +
        (COALESCE(m.rating, 0) * 15) +
        (COUNT(DISTINCT e.id) * 0.5)
    ) DESC
    LIMIT 5
    """, nativeQuery = true)
    List<RecommendedMentorshipProjection> findRecommendedMentorships(
            @Param("studentEmail") String studentEmail,
            @Param("categories") List<String> categories,
            @Param("hasCategories") boolean hasCategories
    );

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto(
        m.id, m.title, m.subtitle, m.description, m.category,
        CONCAT(m.mentor.firstName, ' ', m.mentor.lastName),
        m.price,
        m.discountPercentage,
        m.price * (1.0 - m.discountPercentage / 100.0),
        m.duration, m.coverImageUrl,m.rating
    )
    FROM MentorShip m
    WHERE m.mentor.email = :mentorEmail
    AND m.status = 'ACTIVE'
    AND (
        :studentEmail IS NULL
        OR NOT EXISTS (
            SELECT 1 FROM Enrollment e
            WHERE e.mentorShip.id = m.id
            AND e.student.email = :studentEmail
        )
    )
    ORDER BY m.rating DESC, m.createdAt DESC
""")
    List<MentorshipExploreDto> findTopByMentorEmailOrderByRating(
            @Param("mentorEmail") String mentorEmail,
            @Param("studentEmail") String studentEmail,
            Pageable pageable
    );

    @Query("""
    SELECT 
        m.id as id,
        m.title as title,
        m.subtitle as subtitle,
        m.description as description,
        m.category as category,
        m.difficultyLevel as difficultyLevel,
        m.duration as duration,
        m.price as price,
        m.discountPercentage as discountPercentage,
        m.coverImageUrl as coverImageUrl,
        m.status as status,
        m.rating as rating,
        CONCAT(m.mentor.firstName, ' ', m.mentor.lastName) as mentorName,
        m.mentor.email as mentorEmail,
        m.mentor.profileImageUrl as mentorProfileImageUrl,
        m.mentor.jobTitle as mentorJobTitle,
        m.mentor.yearsOfExperience as mentorYearsOfExperience
    FROM MentorShip m
    WHERE m.id = :mentorshipId
    """)
    MentorshipDetailsProjection findMentorshipDetailsById(@Param("mentorshipId") Long mentorshipId);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent.MentorProfileMentorshipsDto(
        m.id,
        m.title,
        m.subtitle,
        m.category,
        m.difficultyLevel,
        m.price,
        m.discountPercentage,
        m.duration,
        m.coverImageUrl
    )
    FROM MentorShip m
    LEFT JOIN m.enrollments e
    WHERE m.mentor.email = :email
    GROUP BY m.id, m.title, m.subtitle, m.category, m.difficultyLevel, m.price, m.discountPercentage, m.duration, m.coverImageUrl, m.rating
    ORDER BY m.rating DESC, COUNT(e.id) DESC
    """)
    Page<MentorProfileMentorshipsDto> findMentorshipsByMentorEmail(
            @Param("email") String email,
            Pageable pageable
    );

}
