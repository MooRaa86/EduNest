package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission,Long> {
    @Query("SELECT s FROM TaskSubmission s JOIN FETCH s.student WHERE s.task.id = :taskId")
    Page<TaskSubmission> findByTask_Id(Long taskId, Pageable pageable);
    Optional<TaskSubmission> findByTask_IdAndStudent_Id(Long taskId, Long studentId);
    boolean existsByTask_IdAndStudent_Id(Long taskId, Long studentId);

}
