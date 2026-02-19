package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    List<TaskSubmission> findByTask_id (long task_id);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);

}
