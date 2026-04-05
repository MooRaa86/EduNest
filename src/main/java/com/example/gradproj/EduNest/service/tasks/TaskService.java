package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.*;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

public interface TaskService {
    @PreAuthorize("hasRole('MENTOR')")
    TaskResponse createTask(CreateTaskRequest req, MultipartFile file, String email);
    TaskResponse getTaskById(Long taskId);
    @PreAuthorize("hasRole('MENTOR')")
    TaskResponse updateTask(long taskId, PatchTaskRequest request, MultipartFile file, String email);
    @PreAuthorize("hasRole('MENTOR')")
    void deleteTask(Long taskId, String email);
    @PreAuthorize("hasRole('MENTOR')")
    TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest req, String email);
    PageResponse<TaskResponse> getTasks(String taskName, TaskStatus status, Long msid, Pageable pageable);
    @PreAuthorize("hasRole('MENTOR')")
    TaskDashboardDTO getTaskDashboard(Long mentorShipId, String email);
    @PreAuthorize("hasRole('MENTOR')")
    TaskStatisticsDTO getTaskStatistics(Long taskId, Pageable pageable, String email);
    @PreAuthorize("hasRole('MENTOR')")
    FullTaskDashBoardDto getFullTaskDashboard(Long mentorShipId, String taskName, TaskStatus status, Pageable pageable, String email);
    TaskWithSubmissionForStudentResponse getTaskWithSubmission(Long taskId, String email);
}
