package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.users.projection.BadgeProjection;
import com.example.gradproj.EduNest.repository.users.projection.StudentStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmail(String email);
    @Query("select s.id from Student s where s.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);
    List<Student> findAllByEmailIn(Collection<String> emails);

    @Query("""
        SELECT
            COUNT(DISTINCT e.id) as totalEnrollments,
            COUNT(DISTINCT CASE WHEN ms.status = 'COMPLETED' THEN ms.id END) as totalCompletedMentorships,
            COUNT(DISTINCT ba.id) as totalBadgesEarned
        FROM Student s
        LEFT JOIN s.enrollments e
        LEFT JOIN e.mentorShip ms
        LEFT JOIN s.badgeAwards ba
        WHERE s.id = :studentId
        """)
    StudentStatsProjection getStudentStats(@Param("studentId") Long studentId);

    @Query("""
        SELECT
            ba.badge.id as id,
            ba.badge.title as title,
            ba.badge.category as category,
            ba.badge.points as points
        FROM Student s
        JOIN s.badgeAwards ba
        WHERE s.id = :studentId
        """)
    List<BadgeProjection> getStudentBadgeAwards(@Param("studentId") Long studentId);

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.socialMediaLinks WHERE s.id = :id")
    Optional<Student> findStudentWithSocialMediaById(@Param("id") Long id);
}
