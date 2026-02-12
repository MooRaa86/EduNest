package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task,Long> {
    boolean existsById(Long id);
    List<Task> findTaskByStatus(TaskStatus status);
//    List<Task> findByMentorshipId(Long mentorshipId);
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

}
