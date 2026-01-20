package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.TaskResponse;

import java.util.List;

public interface TaskService {
    TaskResponse create(CreateTaskRequest req);
    TaskResponse publish(Long taskId);
    TaskResponse close(Long taskId);
    List<TaskResponse> getPublishedTasks();
    TaskResponse getById(Long taskId);
}
