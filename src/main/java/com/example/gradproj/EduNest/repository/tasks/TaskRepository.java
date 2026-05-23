package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskAuthProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskDashboardProjection;
import com.example.gradproj.EduNest.repository.tasks.projection.UpcomingTaskProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task,Long> {
    @Query("""
        SELECT t.id AS id,
               m.email AS mentorEmail,
               ms.id AS mentorshipId,
               t.uploadedAttachmentPath AS filePath
        FROM Task t
        JOIN t.week w
        JOIN w.mentorship ms
        JOIN ms.mentor m
        WHERE t.id = :id
    """)
    Optional<TaskAuthProjection> findAuthProjectionById(@Param("id") Long id);

    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
        FROM Enrollment e
        WHERE e.mentorShip.id = :mentorshipId
          AND e.student.email = :email
    """)
    boolean isStudentEnrolled(@Param("mentorshipId") Long mentorshipId, @Param("email") String email);

    boolean existsById(Long id);
List<Task> findByWeek_Mentorship_Id(Long mentorshipId);

    @Query("""
    SELECT
        COUNT(t)                                                   AS totalTasks,
        SUM(CASE WHEN t.status = 'PUBLISHED' THEN 1 ELSE 0 END)   AS publishedCount,
        SUM(CASE WHEN t.status = 'DRAFT'     THEN 1 ELSE 0 END)   AS draftCount,
        AVG(COALESCE(s.finalScore, 0))                             AS averageScore
    FROM Task t
    LEFT JOIN t.submissions s
    WHERE t.week.mentorship.id = :mentorshipId
    """)
    TaskDashboardProjection getDashboardStats(@Param("mentorshipId") Long mentorshipId);

    @Query("""
    SELECT t FROM Task t
    WHERE t.week.mentorship.id = :msid
      AND (:taskName IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :taskName, '%')))
      AND (:status IS NULL OR t.status = :status)
""")
    Page<Task> findTasksByMentorship(
            @Param("msid") Long msid,
            @Param("taskName") String taskName,
            @Param("status") TaskStatus status,
            Pageable pageable
    );


    void deleteById(Long taskId);

    List<Task> findByWeek_Id(Long weekId);
    List<Task> findByWeek_IdAndStatusNot(Long weekId, TaskStatus status);
    List<Task> findByWeek_IdIn(List<Long> weekIds);

    @Query("SELECT t FROM Task t WHERE t.week.id IN :weekIds AND t.status != :status")
    List<Task> findByWeek_IdInAndStatusNot(@Param("weekIds") List<Long> weekIds, @Param("status") TaskStatus status);


    @Query("""
    SELECT t.id as id, t.title as title, t.dueAt as dueAt, t.points as points,
           w.id as weekId, w.title as weekTitle,
           m.id as mentorshipId, m.title as mentorshipTitle
    FROM Task t
    JOIN t.week w
    JOIN w.mentorship m
    WHERE t.status = 'PUBLISHED'
      AND t.dueAt > :now
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.email = :email
      )
      AND NOT EXISTS (
        SELECT 1 FROM TaskSubmission ts
        WHERE ts.task.id = t.id
          AND ts.student.email = :email
      )
    ORDER BY t.dueAt ASC
""")
    List<UpcomingTaskProjection> findUpcomingTasksByStudentEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
    SELECT t.id as id, t.title as title, t.dueAt as dueAt, t.points as points,
           w.id as weekId, w.title as weekTitle,
           m.id as mentorshipId, m.title as mentorshipTitle
    FROM Task t
    JOIN t.week w
    JOIN w.mentorship m
    WHERE t.status = 'PUBLISHED'
      AND t.dueAt > :now
      AND m.id = :mentorshipId
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.email = :email
      )
      AND NOT EXISTS (
        SELECT 1 FROM TaskSubmission ts
        WHERE ts.task.id = t.id
          AND ts.student.email = :email
      )
    ORDER BY t.dueAt ASC
""")
    List<UpcomingTaskProjection> findUpcomingTasksByStudentEmailAndMentorship(
            @Param("email") String email,
            @Param("mentorshipId") Long mentorshipId,
            @Param("now") LocalDateTime now
    );

    @Query("""
    SELECT t FROM Task t
    WHERE t.status = :status
      AND t.dueAt > :from
      AND t.dueAt <= :to
    """)
    List<Task> findTasksWithUpcomingDeadline(
            @Param("status") TaskStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

}
