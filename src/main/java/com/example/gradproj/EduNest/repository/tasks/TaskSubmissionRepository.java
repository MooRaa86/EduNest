package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    List<TaskSubmission> findByTask_id (long task_id);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    @Query("""
        select coalesce(sum(ts.finalScore), 0)
        from TaskSubmission ts
        where ts.student.id = :studentId
          and ts.task.mentorship.id = :mentorshipId
          and ts.status = com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.GRADED
          and ts.finalScore is not null
    """)
    int sumFinalScoresForMentorship(@Param("studentId") Long studentId,
                                    @Param("mentorshipId") Long mentorshipId);
}
