package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.*;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.tasks.projection.TaskWithSubmissionProjection;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {
    private final TaskRepository taskRepository;
    private final MentorShipRepository mentorShipRepository;
    private final WeekRepository weekRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final FileStorageService StorageService;

    private void validateMentorshipOwnership(Long mentorShipId, String email) {
        if (!mentorShipRepository.existsByIdAndMentor_Email(mentorShipId, email)) {
            throw new AccessDeniedException("You are not authorized to access this mentorship");
        }
    }

    private Task validateMentorOwnershipAndGetTask(Long taskId, String email) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        if (!mentorShipRepository.existsByIdAndMentor_Email(task.getWeek().getMentorship().getId(), email)) {
            throw new AccessDeniedException("You are not authorized to access this task");
        }
        return task;
    }

    @Override
    public TaskResponse createTask(CreateTaskRequest req, MultipartFile file, String email) {
        if (req.getPassPoints() > req.getPoints()) {
            throw new globalLogicEx("passPoints must be less than or equal to points");
        }
        Week week = weekRepository.findById(req.getWeekId())
                .orElseThrow(() -> new globalLogicEx("weekId not found"));
        validateMentorshipOwnership(week.getMentorship().getId(), email);

        String uploadedPath = null;
        if (file != null && !file.isEmpty()) {
            uploadedPath = StorageService.saveFile("task-attachment", "task",week.getMentorship().getId(), week.getMentorship().getId(), file);
        }

        Task task = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .points(req.getPoints())
                .passPoints(req.getPassPoints())
                .estimatedMinutes(req.getEstimatedMinutes())
                .dueAt(req.getDueAt())
                .attachmentUrl(req.getAttachmentUrl())
                .uploadedAttachmentPath(uploadedPath)
                .status(req.getStatus())
                .week(week)
                .build();
        Task saved = taskRepository.save(task);

        if (saved.getStatus() == TaskStatus.PUBLISHED) {
            notificationService.sendToMentorshipStudents(
                    week.getMentorship().getId(),
                    "New Task",
                    "a new task " + saved.getTitle()
                            + " has been created in week " + week.getTitle() + " in mentorship " + week.getMentorship().getTitle(),
                    NotificationType.TASK
            );
        }

        return mapToTaskResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        return mapToTaskResponse(task);
    }

    @Override
    public TaskResponse updateTask(long taskId, PatchTaskRequest request, MultipartFile file, String email) {
        Task task = validateMentorOwnershipAndGetTask(taskId, email);
        if (task.getStatus() == TaskStatus.CLOSED) {
            throw new globalLogicEx("cannot update closed task");
        }
        TaskStatus oldStatus = task.getStatus();
        if (request != null) {
            if (request.getTitle() != null) task.setTitle(request.getTitle());
            if (request.getDescription() != null) task.setDescription(request.getDescription());
            if (request.getPoints() != null) task.setPoints(request.getPoints());
            if (request.getPassPoints() != null) task.setPassPoints(request.getPassPoints());
            if (request.getEstimatedMinutes() != null) task.setEstimatedMinutes(request.getEstimatedMinutes());
            if (request.getAttachmentUrl() != null) task.setAttachmentUrl(request.getAttachmentUrl());
            if (request.getStatus() != null) task.setStatus(request.getStatus());
            if (request.getDueAt() != null) {
                if (request.getDueAt().isBefore(LocalDateTime.now())) {
                    throw new globalLogicEx("dueAt must be in the future ");
                }
                task.setDueAt(request.getDueAt());
            }
        }
        if (file != null && !file.isEmpty()) {
            Long mentorshipId = task.getWeek().getMentorship().getId();
            String oldPath = task.getUploadedAttachmentPath();
            if (oldPath != null && !oldPath.isBlank()) {
                StorageService.deleteFile(oldPath);
            }
            task.setUploadedAttachmentPath(StorageService.saveFile("task-attachment","task", mentorshipId, mentorshipId, file));
        }
        if (task.getPassPoints() > task.getPoints()) {
            throw new globalLogicEx("Pass points must be less than or equal to points.");
        }
        
        if (oldStatus != TaskStatus.PUBLISHED && task.getStatus() == TaskStatus.PUBLISHED) {
            Week week = task.getWeek();
            notificationService.sendToMentorshipStudents(
                    week.getMentorship().getId(),
                    "New Task",
                    "a new task " + task.getTitle()
                            + " has been created in week " + week.getTitle() + " in mentorship " + week.getMentorship().getTitle(),
                    NotificationType.TASK
            );
        }
        
        return mapToTaskResponse(task);
    }

    @Override
    public void deleteTask(Long taskId, String email) {
        Task task = validateMentorOwnershipAndGetTask(taskId, email);

        String filePath = task.getUploadedAttachmentPath();
        if (filePath != null && !filePath.isBlank()) {
            StorageService.deleteFile(filePath);
        }

        taskRepository.deleteById(taskId);
    }

    @Override
    public TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest req, String email) {
        Task task = validateMentorOwnershipAndGetTask(taskId, email);
        if (task.getStatus() == TaskStatus.PUBLISHED && req.getStatus() == TaskStatus.DRAFT) {
            throw new globalLogicEx("Cannot revert published task to draft");
        }
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(req.getStatus());
        
        if (oldStatus != TaskStatus.PUBLISHED && req.getStatus() == TaskStatus.PUBLISHED) {
            Week week = task.getWeek();
            notificationService.sendToMentorshipStudents(
                    week.getMentorship().getId(),
                    "New Task",
                    "a new task " + task.getTitle()
                            + " has been created in week " + week.getTitle() + " in mentorship " + week.getMentorship().getTitle(),
                    NotificationType.TASK
            );
        }
        
        return mapToTaskResponse(task);
    }

    private TaskResponse mapToTaskResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .points(task.getPoints())
                .passPoints(task.getPassPoints())
                .estimatedMinutes(task.getEstimatedMinutes())
                .status(task.getStatus().name())
                .dueAt(task.getDueAt())
                .attachmentUrl(task.getAttachmentUrl())
                .uploadedAttachmentPath(task.getUploadedAttachmentPath())
                .build();
    }

    @Override
    public PageResponse<TaskResponse> getTasks(String taskName, TaskStatus status, Long msid, Pageable pageable) {
        Page<Task> tasks = taskRepository.findTasksByMentorship(msid, taskName, status, pageable);
        List<TaskResponse> taskDTOs = tasks.getContent().stream()
                .map(task -> TaskResponse.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .description(task.getDescription())
                        .points(task.getPoints())
                        .passPoints(task.getPassPoints())
                        .estimatedMinutes(task.getEstimatedMinutes())
                        .status(String.valueOf(task.getStatus()))
                        .dueAt(task.getDueAt())
                        .attachmentUrl(task.getAttachmentUrl())
                        .uploadedAttachmentPath(task.getUploadedAttachmentPath())
                        .build())
                .toList();

        return PageResponse.<TaskResponse>builder()
                .content(taskDTOs)
                .page(tasks.getNumber())
                .size(tasks.getSize())
                .totalElements(tasks.getTotalElements())
                .totalPages(tasks.getTotalPages())
                .build();
    }

    @Override
    public TaskDashboardDTO getTaskDashboard(Long mentorShipId, String email) {
        validateMentorshipOwnership(mentorShipId, email);
        var stats = taskRepository.getDashboardStats(mentorShipId);
        return TaskDashboardDTO.builder()
                .totalTasks(stats.getTotalTasks() != null ? stats.getTotalTasks().intValue() : 0)
                .publishedCount(stats.getPublishedCount() != null ? stats.getPublishedCount().intValue() : 0)
                .draftCount(stats.getDraftCount() != null ? stats.getDraftCount().intValue() : 0)
                .averageScore(stats.getAverageScore() != null ? stats.getAverageScore() : 0.0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatisticsDTO getTaskStatistics(Long taskId, Pageable pageable, String email) {
        Task task = validateMentorOwnershipAndGetTask(taskId, email);
        Long mentorshipId = task.getWeek().getMentorship().getId();
        int totalStudents = (int) enrollmentRepository.countStudentsByMentorship(mentorshipId);

        Page<TaskSubmission> submissionsPage = taskSubmissionRepository.findByTask_Id(taskId, pageable);
        Page<TaskSubmissionResponse> mapped = submissionsPage.map(this::mapToSubmissionResponseForStats);

        PageResponse<TaskSubmissionResponse> pageResponse = PageResponse.<TaskSubmissionResponse>builder()
                .content(mapped.getContent())
                .page(mapped.getNumber())
                .size(mapped.getSize())
                .totalElements(mapped.getTotalElements())
                .totalPages(mapped.getTotalPages())
                .build();

        return TaskStatisticsDTO.builder()
                .status(task.getStatus())
                .taskTitle(task.getTitle())
                .totalStudents(totalStudents)
                .totalSubmissions((int) submissionsPage.getTotalElements())
                .pendingReview(totalStudents - submissionsPage.getTotalPages())
                .createdAt(task.getCreatedAt())
                .deadLine(task.getDueAt())
                .totalPoints(task.getPoints())
                .taskSubmissionResponsePageResponse(pageResponse)
                .build();
    }

    private TaskSubmissionResponse mapToSubmissionResponseForStats(TaskSubmission s) {
        return TaskSubmissionResponse.builder()
                .submissionId(s.getId())
                .taskId(s.getTask().getId())
                .studentId(s.getStudent().getId())
                .studentFullName(s.getStudent().getFirstName() + " " + s.getStudent().getLastName())
                .fileUrl(s.getFileUrl())
                .uploadedFilePath(s.getUploadedFilePath())
                .status(s.getStatus())
                .isLate(s.getIsLate())
                .rawScore(s.getRawScore())
                .finalScore(s.getFinalScore())
                .submittedAt(s.getSubmittedAt())
                .feedback(s.getFeedBack())
                .build();
    }

    @Override
    public FullTaskDashBoardDto getFullTaskDashboard(Long mentorShipId, String taskName, TaskStatus status, Pageable pageable, String email) {
        return FullTaskDashBoardDto.builder()
                .taskDashboardDTO(getTaskDashboard(mentorShipId, email))
                .taskResponsePageResponse(getTasks(taskName, status, mentorShipId, pageable))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskWithSubmissionForStudentResponse getTaskWithSubmission(Long taskId, String email) {
        TaskWithSubmissionProjection p = taskSubmissionRepository.findTaskWithSubmission(taskId, email);
        if (p == null) throw new globalLogicEx("Task not found");
        return TaskWithSubmissionForStudentResponse.builder()
                .taskId(p.getTaskId())
                .taskTitle(p.getTaskTitle())
                .points(p.getPoints())
                .dueAt(p.getDueAt())
                .description(p.getDescription())
                .estimatedMinutes(p.getEstimatedMinutes())
                .attachmentUrl(p.getAttachmentUrl())
                .uploadedAttachmentPath(p.getUploadedAttachmentPath())
                .submissionUrl(p.getFileUrl())
                .score(p.getFinalScore())
                .totalPoints(p.getTotalPoints())
                .submissionStatus(p.getSubmissionStatus())
                .feedback(p.getFeedback())
                .mentorName(p.getMentorName())
                .mentorPhoto(p.getMentorPhoto())
                .build();
    }
}
