package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task,Long> {
    boolean existsById(Long id);
    List<Task> findTaskByStatus(TaskStatus status);
    List<Task> findByMentorshipId(Long mentorshipId);
    Page<Task> findByMentorshipId(Long mentorshipId, Pageable pageable);
    List<Task> findByMentorshipIdAndStatus(Long mentorshipId, TaskStatus status);

}
