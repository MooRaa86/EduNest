package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    Page<TaskSubmission> findByTask_Id(Long taskId, Pageable pageable);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    boolean existsByTask_IdAndStudent_Id(Long taskId, Long studentId);

}
