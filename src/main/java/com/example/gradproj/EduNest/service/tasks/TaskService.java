package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;

import java.util.List;

public interface TaskService {
    TaskResponse create(CreateTaskRequest req);
    TaskResponse publish(Long taskId);
    TaskResponse close(Long taskId);
    List<TaskResponse> getPublishedTasks();
    TaskResponse getById(Long taskId);
    TaskResponse update(long taskId, PatchTaskRequest request);
    void delete(Long taskId);
}
