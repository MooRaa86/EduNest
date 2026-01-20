package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.TaskResponse;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.utils.SystemUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@Transactional
public class TaskServiceImpl implements TaskService{
    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }


    @Override
    public TaskResponse create(CreateTaskRequest req) {
        if (req.getPassPoints()> req.getPoints()){
            throw new IllegalArgumentException("passPoints must be <= points");
        }
        Task task= Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .points(req.getPoints())
                .passPoints(req.getPassPoints())
                .estimatedMinutes(req.getEstimateMinutes())
                .dueAt(req.getDueAt())
                .attachmentUrl(req.getAttachmentUrl())
                .status(TaskStatus.DRAFT)
                .build();
        task.setCreatedBy(SystemUtils.MENTOR);
        task.setUpdatedBy(SystemUtils.MENTOR);

        Task saved=taskRepository.save(task);

        return mapToTaskResponse(saved);

    }

    @Override
    public TaskResponse publish(Long taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        task.setStatus(TaskStatus.PUBLISHED);
        task.setUpdatedBy(SystemUtils.MENTOR);
        Task updatedTask = taskRepository.save(task);
        return mapToTaskResponse(updatedTask);
    }

    @Override
    public TaskResponse close(Long taskId) {
        Task task=taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        task.setStatus(TaskStatus.CLOSED);
        task.setUpdatedBy(SystemUtils.MENTOR);
        Task closedTask=taskRepository.save(task);
        return  mapToTaskResponse(closedTask);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getPublishedTasks() {
        return taskRepository.findTaskByStatus(TaskStatus.PUBLISHED).stream()
                .map(this::mapToTaskResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(Long taskId) {

        Task task=taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));
        return  mapToTaskResponse(task);
    }
    private TaskResponse mapToTaskResponse(Task task) {
        TaskResponse res = new TaskResponse();
        res.setId(task.getId());
        res.setTitle(task.getTitle());
        res.setDescription(task.getDescription());
        res.setPoints(task.getPoints());
        res.setPassPoints(task.getPassPoints());
        res.setEstimatedMinutes(task.getEstimatedMinutes());
        res.setMaxAttempts(task.getMaxAttempts());
        res.setStatus(task.getStatus().name());
        res.setDueAt(task.getDueAt());
        res.setAttachmentUrl(task.getAttachmentUrl());
        return res;
    }
}
