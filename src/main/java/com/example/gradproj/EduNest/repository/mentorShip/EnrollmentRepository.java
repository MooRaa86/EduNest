package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.repository.mentorShip.projections.EnrolledMentorshipProgressResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorStudentListResponse;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MonthlyRevenueProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.StudentMentorProfileKpiResponse;
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
// profile total enrolled mentorships
@Query(
        value = """
        SELECT
            m.id AS mentorshipId,
            m.title AS title,
            m.status AS status,

            COALESCE(
                (SELECT tp.totalPoints
                 FROM TotalPoints tp
                 WHERE tp.student.id = :studentId
                   AND tp.mentorship.id = m.id),
                0
            ) AS totalPoints,

            (SELECT COUNT(DISTINCT t.id)
             FROM Task t
             WHERE t.week.mentorship.id = m.id) AS totalTasks,

            (SELECT COUNT(DISTINCT ts.task.id)
             FROM TaskSubmission ts
             WHERE ts.student.id = :studentId
               AND ts.task.week.mentorship.id = m.id
               AND ts.status IN (
                    com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.SUBMITTED,
                    com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.GRADED
               )
            ) AS submittedTasks,

            (SELECT COUNT(DISTINCT q.id)
             FROM Quiz q
             WHERE q.week.mentorship.id = m.id) AS totalQuizzes,

            (SELECT COUNT(DISTINCT qs.quiz.id)
             FROM QuizSubmission qs
             WHERE qs.student.id = :studentId
               AND qs.quiz.week.mentorship.id = m.id
            ) AS submittedQuizzes

        FROM Enrollment e
        JOIN e.mentorShip m
        WHERE e.student.id = :studentId
          AND m.mentor.id = :mentorId
    """,
        countQuery = """
        SELECT COUNT(e.id)
        FROM Enrollment e
        JOIN e.mentorShip m
        WHERE e.student.id = :studentId
          AND m.mentor.id = :mentorId
    """
)
Page<EnrolledMentorshipProgressResponse> findEnrolledMentorshipsProgressForMentorAndStudent(
        @Param("mentorId") Long mentorId,
        @Param("studentId") Long studentId,
        Pageable pageable
);



    @Query("""
    SELECT
        COALESCE(SUM(tp.totalPoints), 0) AS totalPoints,

        SUM(CASE WHEN m.status = com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE THEN 1 ELSE 0 END)
        AS activeMentorships,

        SUM(CASE WHEN m.status = com.example.gradproj.EduNest.enums.mentorShip.Status.COMPLETED THEN 1 ELSE 0 END)
        AS completedMentorships

    FROM Enrollment e
    JOIN e.mentorShip m

    LEFT JOIN TotalPoints tp
           ON tp.student.id = :studentId
          AND tp.mentorship.id = m.id

    WHERE e.student.id = :studentId
      AND m.mentor.id = :mentorId
""")
    StudentMentorProfileKpiResponse getStudentMentorProfileKpis(
            @Param("mentorId") Long mentorId,
            @Param("studentId") Long studentId
    );


    @Query("""
    SELECT COUNT(e) > 0
    FROM Enrollment e
    WHERE e.mentorShip.id = (
        SELECT r.mentorship.id
        FROM ChatRoom r
        WHERE r.id = :roomId
    )
    AND e.student.email = :email
""")
    boolean isUserInRoomMentorship(
            Long roomId,
            String email
    );

    int countByMentorShip(MentorShip mentorShip);
    boolean existsByMentorShip_IdAndStudent_Id(Long mentorshipId, Long studentId);

    @Query("""
        select (count(e) > 0)
        from Enrollment e
        where e.student.id = :studentId
          and e.mentorShip.id =
              (select t.week.mentorship.id
               from Task t
               where t.id = :taskId)
    """)
    boolean isStudentEnrolledForTask(@Param("taskId") Long taskId,
                                     @Param("studentId") Long studentId);

    @Query("""
        select (count(e) > 0)
        from Enrollment e
        where e.student.id = :studentId
          and e.mentorShip.id =
              (select p.week.mentorship.id
               from Project p
               where p.id = :projectId)
    """)
    boolean isStudentEnrolledForProject(@Param("projectId") Long projectId,
                                        @Param("studentId") Long studentId);
}
