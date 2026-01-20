package com.example.gradproj.EduNest.repository.tasks;

import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task,Long> {
List<Task> findTaskByStatus(TaskStatus status);
}
