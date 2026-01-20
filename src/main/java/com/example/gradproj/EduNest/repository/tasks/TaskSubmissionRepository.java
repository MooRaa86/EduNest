package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    int countByTask_idAndStudentId(Long task_id,Long student_id);
    List<TaskSubmission> findByTask_id (long task_id);

    List<TaskSubmission> findByTask_idAndStudentIdOrderByAttemptNoDesc(long task_id,Long student_id);

    @Query("select max(s.attemptNo) from TaskSubmission s where s.task.id = :taskId and s.studentId = :studentId")
    Integer findMaxAttemptNo(@Param("taskId") Long taskId, @Param("studentId") Long studentId);
}
