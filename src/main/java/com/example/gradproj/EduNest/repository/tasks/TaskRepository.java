package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.projection.UpcomingTaskProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task,Long> {
    boolean existsById(Long id);
List<Task> findByWeek_Mentorship_Id(Long mentorshipId);

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

}
