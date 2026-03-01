package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.FullTaskDashBoardDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskDashboardDTO;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.dto.tasks.response.TaskStatisticsDTO;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.domain.Pageable;

public interface TaskService {
    TaskResponse createTask(CreateTaskRequest req);
    TaskResponse getTaskById(Long taskId);
    TaskResponse updateTask(long taskId, PatchTaskRequest request);
    void deleteTask(Long taskId);
    TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest req);
    PageResponse<TaskResponse> getTasks(String taskName, TaskStatus status, Long msid, Pageable pageable);
    TaskDashboardDTO getTaskDashboard(Long mentorShipId);
    public TaskStatisticsDTO getTaskStatistics(Long taskId, Pageable pageable);
    FullTaskDashBoardDto getFullTaskDashboard(Long mentorShipId, String taskName, TaskStatus status, Pageable pageable);
}

