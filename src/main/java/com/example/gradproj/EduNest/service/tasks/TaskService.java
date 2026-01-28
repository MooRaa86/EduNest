package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;


import java.util.List;

public interface TaskService {
    TaskResponse create(CreateTaskRequest req);
    TaskResponse getById(Long taskId);
    TaskResponse update(long taskId, PatchTaskRequest request);
    void delete(Long taskId);
    TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest req);
    PageResponse<TaskResponse> getTasks(String taskName, TaskStatus status, Long msid, Pageable pageable);
}
