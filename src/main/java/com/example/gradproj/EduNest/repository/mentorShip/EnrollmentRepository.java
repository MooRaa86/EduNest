package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.mentorShip.projections.*;
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

    boolean existsByMentorShip_IdAndStudent_Email(Long mentorShipId, String studentEmail);

    boolean existsByMentorShip_IdAndStudent_Id(Long mentorShipId, Long studentId);

    @Query("""
        SELECT COUNT(e) > 0 FROM Enrollment e
        WHERE e.student.id = :studentId
          AND e.mentorShip.id = (SELECT w.mentorship.id FROM Week w WHERE w.id = :weekId)
    """)
    boolean isStudentEnrolledInWeekMentorship(@Param("weekId") Long weekId,
                                              @Param("studentId") Long studentId);

    @Query("""
        SELECT COUNT(e) > 0 FROM Enrollment e
        JOIN Week w ON w.mentorship.id = e.mentorShip.id
        WHERE e.student.email = :studentEmail
          AND w.id = :weekId
    """)
    boolean isStudentEnrolledInWeekMentorshipByEmail(@Param("weekId") Long weekId,
                                                      @Param("studentEmail") String studentEmail);

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
        where e.student.email = :email
          and e.mentorShip.id =
              (select p.week.mentorship.id
               from Project p
               where p.id = :projectId)
    """)
    boolean isStudentEnrolledForProjectByEmail(@Param("projectId") Long projectId,
                                               @Param("email") String email);
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

    @Query(value = """
    SELECT 
        m.id as mentorshipId,
        m.title as title,
        m.cover_image_url as coverImageUrl,
        CONCAT(u.first_name, ' ', u.last_name) as mentorName,
        COUNT(DISTINCT CASE WHEN t.task_status = 'PUBLISHED' THEN t.id END) as totalTasks,
        COUNT(DISTINCT ts.id) as completedTasks,
        COUNT(DISTINCT CASE WHEN q.status = 'PUBLISHED' THEN q.id END) as totalQuizzes,
        COUNT(DISTINCT qs.id) as completedQuizzes,
        COUNT(DISTINCT CASE WHEN p.project_status = 'PUBLISHED' THEN p.id END) as totalProjects,
        COUNT(DISTINCT ps.id) as completedProjects
    FROM enrollments e
    JOIN mentorship m ON e.mentorship_id = m.id
    JOIN mentors mentor ON m.mentor_id = mentor.mentor_id
    JOIN users u ON u.id = mentor.mentor_id
    JOIN students s ON s.student_id = e.student_id
    JOIN users student_user ON student_user.id = s.student_id
    LEFT JOIN weeks w ON w.mentorship_id = m.id
    LEFT JOIN tasks t ON t.week_id = w.id
    LEFT JOIN task_submission ts ON ts.task_id = t.id AND ts.student_id = s.student_id
    LEFT JOIN quiz q ON q.week_id = w.id
    LEFT JOIN quiz_submission qs ON qs.quiz_id = q.id AND qs.student_id = s.student_id
    LEFT JOIN projects p ON p.week_id = w.id
    LEFT JOIN project_submission ps ON ps.project_id = p.id AND ps.student_id = s.student_id
    WHERE student_user.email = :email
      AND m.status = 'ACTIVE'
    GROUP BY m.id, m.title, m.cover_image_url, u.first_name, u.last_name
    ORDER BY (
        (COUNT(DISTINCT CASE WHEN t.task_status = 'PUBLISHED' THEN t.id END) - COUNT(DISTINCT ts.id)) +
        (COUNT(DISTINCT CASE WHEN q.status = 'PUBLISHED' THEN q.id END) - COUNT(DISTINCT qs.id)) +
        (COUNT(DISTINCT CASE WHEN p.project_status = 'PUBLISHED' THEN p.id END) - COUNT(DISTINCT ps.id))
    ) DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<ContinueLearningWithProgressProjection> findContinueLearningWithProgress(
            @Param("email") String email,
            @Param("limit") int limit
    );

    @Query(value = """
    SELECT 
        COUNT(DISTINCT CASE WHEN t.task_status = 'PUBLISHED' THEN t.id END) as totalTasks,
        COUNT(DISTINCT CASE WHEN ts.student_id IS NOT NULL THEN ts.id END) as completedTasks,
        COUNT(DISTINCT CASE WHEN q.status = 'PUBLISHED' THEN q.id END) as totalQuizzes,
        COUNT(DISTINCT CASE WHEN qs.student_id IS NOT NULL THEN qs.id END) as completedQuizzes,
        COUNT(DISTINCT CASE WHEN p.project_status = 'PUBLISHED' THEN p.id END) as totalProjects,
        COUNT(DISTINCT CASE WHEN ps.student_id IS NOT NULL THEN ps.id END) as completedProjects
    FROM weeks w
    LEFT JOIN tasks t ON t.week_id = w.id
    LEFT JOIN task_submission ts ON ts.task_id = t.id AND ts.student_id = (SELECT s.student_id FROM students s JOIN users u ON s.student_id = u.id WHERE u.email = :email)
    LEFT JOIN quiz q ON q.week_id = w.id
    LEFT JOIN quiz_submission qs ON qs.quiz_id = q.id AND qs.student_id = (SELECT s.student_id FROM students s JOIN users u ON s.student_id = u.id WHERE u.email = :email)
    LEFT JOIN projects p ON p.week_id = w.id
    LEFT JOIN project_submission ps ON ps.project_id = p.id AND ps.student_id = (SELECT s.student_id FROM students s JOIN users u ON s.student_id = u.id WHERE u.email = :email)
    WHERE w.mentorship_id = :mentorshipId
    """, nativeQuery = true)
    ContentProgressProjection getContentProgress(
            @Param("mentorshipId") Long mentorshipId,
            @Param("email") String email
    );
}
