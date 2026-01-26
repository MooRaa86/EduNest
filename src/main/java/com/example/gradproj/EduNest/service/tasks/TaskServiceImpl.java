package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
@Transactional
public class TaskServiceImpl implements TaskService{
    private final TaskRepository taskRepository;
    private final mentorShipRepository mentorShipRepository;

    public TaskServiceImpl(TaskRepository taskRepository,mentorShipRepository mentorShipRepository) {
        this.taskRepository = taskRepository;
        this.mentorShipRepository=mentorShipRepository;
    }


    @Override
    public TaskResponse create(CreateTaskRequest req) {
        if (req.getPassPoints()> req.getPoints()){
            throw new globalLogicEx("passPoints must be less than or equal to points");
        }
        mentorShipE mentorship = mentorShipRepository.findById(req.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));


        Task task= Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .points(req.getPoints())
                .passPoints(req.getPassPoints())
                .estimatedMinutes(req.getEstimatedMinutes())
                .dueAt(req.getDueAt())
                .attachmentUrl(req.getAttachmentUrl())
                .status(TaskStatus.DRAFT)
                .mentorship(mentorship)
                .build();
        Task saved=taskRepository.save(task);

        return mapToTaskResponse(saved);

    }

    @Override
    public TaskResponse publish(Long taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new globalLogicEx("Task not found"));
        task.setStatus(TaskStatus.PUBLISHED);
        Task updatedTask = taskRepository.save(task);
        return mapToTaskResponse(updatedTask);
    }

    @Override
    public TaskResponse close(Long taskId) {
        Task task=taskRepository.findById(taskId)
            .orElseThrow(() -> new globalLogicEx("Task not found"));
        task.setStatus(TaskStatus.CLOSED);
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
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        return  mapToTaskResponse(task);
    }

    @Override
    public TaskResponse update(long taskId, PatchTaskRequest request) {
        Task task=taskRepository.findById(taskId).orElseThrow(()->new IllegalArgumentException("Task not found with id: " + taskId));
        if (task.getStatus() == TaskStatus.CLOSED){
            throw new globalLogicEx("cannot update closed task");
        }
        if (request.getTitle() !=null)task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPoints() != null) task.setPoints(request.getPoints());
        if (request.getPassPoints() != null) task.setPassPoints(request.getPassPoints());
        if (request.getEstimatedMinutes() != null) task.setEstimatedMinutes(request.getEstimatedMinutes());
        if (request.getAttachmentUrl() != null) task.setAttachmentUrl(request.getAttachmentUrl());
        if (request.getDueAt() != null){
            if (request.getDueAt().isBefore(LocalDateTime.now())){
                throw new globalLogicEx("dueAt must be in the future ");
            }
            task.setDueAt(request.getDueAt());

        }
        if (task.getPassPoints()>task.getPoints()){
            throw  new globalLogicEx("Pass points must be less than or equal to points.");
        }
        return mapToTaskResponse(task);
    }

    @Override
    public void delete(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(()->new globalLogicEx("Task not found"));
        taskRepository.delete(task);
    }

    private TaskResponse mapToTaskResponse(Task task) {
        TaskResponse res = new TaskResponse();
        res.setId(task.getId());
        res.setTitle(task.getTitle());
        res.setDescription(task.getDescription());
        res.setPoints(task.getPoints());
        res.setPassPoints(task.getPassPoints());
        res.setEstimatedMinutes(task.getEstimatedMinutes());
        res.setStatus(task.getStatus().name());
        res.setDueAt(task.getDueAt());
        res.setAttachmentUrl(task.getAttachmentUrl());

        return res;
    }
}
