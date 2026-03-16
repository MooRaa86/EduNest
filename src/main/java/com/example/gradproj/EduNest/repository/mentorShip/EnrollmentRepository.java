package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.mentorShip.projections.ContentProgressProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.ContinueLearningProjection;
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
        COALESCE(tp.totalPoints, 0) AS totalPoints,
        COUNT(DISTINCT t.id) AS totalTasks,
        COUNT(DISTINCT ts.task.id) AS submittedTasks,
        COUNT(DISTINCT q.id) AS totalQuizzes,
        COUNT(DISTINCT qs.quiz.id) AS submittedQuizzes
    FROM Enrollment e
    JOIN e.mentorShip m
    LEFT JOIN m.weeks w
    LEFT JOIN TotalPoints tp ON tp.student.id = :studentId AND tp.mentorship.id = m.id
    LEFT JOIN w.tasks t
    LEFT JOIN TaskSubmission ts
        ON ts.student.id = :studentId
        AND ts.task.id = t.id
        AND ts.status IN (
            com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.SUBMITTED,
            com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.GRADED
        )
    LEFT JOIN w.quizzes q
    LEFT JOIN QuizSubmission qs ON qs.student.id = :studentId AND qs.quiz.id = q.id
    WHERE e.student.id = :studentId
      AND m.mentor.id = :mentorId
    GROUP BY m.id, m.title, m.status, tp.totalPoints
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
    @Query("""
    select count(e)
    from Enrollment e
    where e.mentorShip.id = :mentorShipId
""")
    long countStudentsByMentorship(@Param("mentorShipId") Long mentorShipId);

    @Query("""
       select e.student
       from Enrollment e
       where e.mentorShip.id = :mentorshipId
       """)
    List<Student> findStudentsByMentorshipId(Long mentorshipId);

    @Query("""
        SELECT COUNT(e) > 0
        FROM Enrollment e
        WHERE e.mentorShip.mentor.id = :mentorId
          AND e.student.id = :studentId
    """)
    boolean existsByMentorIdAndStudentId(@Param("mentorId") Long mentorId,
                                         @Param("studentId") Long studentId);

    @Query("""
    SELECT 
        m.id as mentorshipId,
        m.title as title,
        m.coverImageUrl as coverImageUrl,
        CONCAT(mentor.firstName, ' ', mentor.lastName) as mentorName,
        m.status as status,
        COUNT(DISTINCT w.id) as totalWeeks,
        e.joinedAt as joinedAt
    FROM Enrollment e
    JOIN e.mentorShip m
    JOIN m.mentor mentor
    LEFT JOIN m.weeks w
    WHERE e.student.email = :email
      AND m.status = com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE
    GROUP BY m.id, m.title, m.coverImageUrl, mentor.firstName, mentor.lastName, m.status, e.joinedAt
    ORDER BY e.joinedAt DESC
    """)
    List<ContinueLearningProjection> findContinueLearningByStudentEmail(
            @Param("email") String email,
            Pageable pageable
    );

    @Query(value = """
    SELECT 
        COALESCE(SUM(totalTasks), 0) as totalTasks,
        COALESCE(SUM(completedTasks), 0) as completedTasks,
        COALESCE(SUM(totalQuizzes), 0) as totalQuizzes,
        COALESCE(SUM(completedQuizzes), 0) as completedQuizzes,
        COALESCE(SUM(totalProjects), 0) as totalProjects,
        COALESCE(SUM(completedProjects), 0) as completedProjects
    FROM (
        SELECT 
            COUNT(DISTINCT t.id) as totalTasks,
            COUNT(DISTINCT ts.id) as completedTasks,
            0 as totalQuizzes,
            0 as completedQuizzes,
            0 as totalProjects,
            0 as completedProjects
        FROM tasks t
        JOIN weeks w ON t.week_id = w.id
        LEFT JOIN task_submission ts ON ts.task_id = t.id
        LEFT JOIN students s ON ts.student_id = s.student_id
        LEFT JOIN users u ON s.student_id = u.id AND u.email = :email
        WHERE w.mentorship_id = :mentorshipId
          AND t.task_status = 'PUBLISHED'
        
        UNION ALL
        
        SELECT 
            0 as totalTasks,
            0 as completedTasks,
            COUNT(DISTINCT q.id) as totalQuizzes,
            COUNT(DISTINCT qs.id) as completedQuizzes,
            0 as totalProjects,
            0 as completedProjects
        FROM quiz q
        JOIN weeks w ON q.week_id = w.id
        LEFT JOIN quiz_submission qs ON qs.quiz_id = q.id
        LEFT JOIN students s ON qs.student_id = s.student_id
        LEFT JOIN users u ON s.student_id = u.id AND u.email = :email
        WHERE w.mentorship_id = :mentorshipId
          AND q.status = 'PUBLISHED'
        
        UNION ALL
        
        SELECT 
            0 as totalTasks,
            0 as completedTasks,
            0 as totalQuizzes,
            0 as completedQuizzes,
            COUNT(DISTINCT p.id) as totalProjects,
            COUNT(DISTINCT ps.id) as completedProjects
        FROM projects p
        JOIN weeks w ON p.week_id = w.id
        LEFT JOIN project_submission ps ON ps.project_id = p.id
        LEFT JOIN students s ON ps.student_id = s.student_id
        LEFT JOIN users u ON s.student_id = u.id AND u.email = :email
        WHERE w.mentorship_id = :mentorshipId
          AND p.project_status = 'PUBLISHED'
    ) combined
    """, nativeQuery = true)
    ContentProgressProjection getContentProgress(
            @Param("mentorshipId") Long mentorshipId,
            @Param("email") String email
    );
}
