package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorStudentListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MonthlyRevenueProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("""
        SELECT COUNT(e.id)
        FROM Enrollment e
        WHERE e.mentorShip.mentor.id = :mentorId
    """)
    long countStudentsByMentorId(@Param("mentorId") Long mentorId);

    @Query("""
    SELECT COALESCE(SUM(e.price), 0)
    FROM Enrollment e
    WHERE e.mentorShip.mentor.id = :mentorId
""")
    Double getTotalRevenueByMentorId(@Param("mentorId") Long mentorId);

    @Query("""
    SELECT 
        YEAR(e.joinedAt) as year,
        MONTH(e.joinedAt) as month,
        COALESCE(SUM(e.price), 0) as totalRevenue
    FROM Enrollment e
    WHERE e.mentorShip.mentor.email = :email
      AND (:startDate IS NULL OR e.joinedAt >= :startDate)
    GROUP BY YEAR(e.joinedAt), MONTH(e.joinedAt)
    ORDER BY YEAR(e.joinedAt), MONTH(e.joinedAt)
""")
    List<MonthlyRevenueProjection> getMonthlyRevenueForMentor(
            @Param("email") String email,
            @Param("startDate") LocalDateTime startDate
    );

    @Query("""
    SELECT 
        e.student.id AS studentId,
        e.student.firstName AS firstName,
        e.student.lastName AS lastName,
        e.student.email AS email,

        SUM(CASE WHEN m.status = 'ACTIVE' THEN 1 ELSE 0 END) AS activeMentorshipCount,
        SUM(CASE WHEN m.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedMentorshipCount

    FROM Enrollment e
    JOIN e.mentorShip m
    WHERE m.mentor.email = :email
    GROUP BY e.student.id, e.student.firstName, e.student.lastName, e.student.email
""")
    Page<MentorStudentListResponse> findStudentsForMentor(
            @Param("email") String email,
            Pageable pageable
    );



    List<Enrollment> findByStudent_Id(long studentId);

    List<Enrollment> findByMentorShip_Id(long mentorShipId);

    Enrollment findByMentorShipAndStudent(MentorShip mentorShip, Student student);

    int countByMentorShip(MentorShip mentorShip);
    boolean existsByMentorShip_IdAndStudent_Id(Long mentorshipId, Long studentId);
}
