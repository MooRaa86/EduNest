package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.AllMentorShipsExplorePage;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorMentorshipProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorShipListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorshipStatsResponse;
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

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.mentorShipDTOs.response.AllMentorShipsExplorePage(
        m.id, m.title, m.subtitle, m.description, m.category,
        CONCAT(m.mentor.firstName, ' ', m.mentor.lastName) as mentorName,
        m.price,
        m.price * (1.0 - m.discountPercentage / 100.0),
        m.duration, m.coverImageUrl
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
    Page<AllMentorShipsExplorePage> searchMentorShips(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );

    @Query("SELECT DISTINCT m.category FROM MentorShip m WHERE m.status = 'ACTIVE' AND m.mentor.deleted = false")
    List<String> findAllCategories();

}
