package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskDashboardDTO;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.dto.tasks.response.TaskStatisticsDTO;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
@Transactional
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService{
    private final TaskRepository taskRepository;
    private final MentorShipRepository mentorShipRepository;
    private final WeekRepository weekRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final EnrollmentRepository enrollmentRepository;




    @Override
    public TaskResponse createTask(CreateTaskRequest req) {
        if (req.getPassPoints()> req.getPoints()){
            throw new globalLogicEx("passPoints must be less than or equal to points");
        }
        Week week=weekRepository.findById(req.getWeekId()).orElseThrow(
                ()->new  globalLogicEx("weekId not found")
        );


        Task task= Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .points(req.getPoints())
                .passPoints(req.getPassPoints())
                .estimatedMinutes(req.getEstimatedMinutes())
                .dueAt(req.getDueAt())
                .attachmentUrl(req.getAttachmentUrl())
                .status(req.getStatus())
                .week(week)
                .build();
        Task saved=taskRepository.save(task);

        return mapToTaskResponse(saved);

    }


    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId) {

        Task task=taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        return  mapToTaskResponse(task);
    }

    @Override
    public TaskResponse updateTask(long taskId, PatchTaskRequest request) {
        Task task=taskRepository.findById(taskId).orElseThrow(()->new IllegalArgumentException("Task not found "));
        if (task.getStatus() == TaskStatus.CLOSED){
            throw new globalLogicEx("cannot update closed task");
        }
        if (request.getTitle() !=null)task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPoints() != null) task.setPoints(request.getPoints());
        if (request.getPassPoints() != null) task.setPassPoints(request.getPassPoints());
        if (request.getEstimatedMinutes() != null) task.setEstimatedMinutes(request.getEstimatedMinutes());
        if (request.getAttachmentUrl() != null) task.setAttachmentUrl(request.getAttachmentUrl());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
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
    public void deleteTask(Long taskId) {
       if (!(taskRepository.existsById(taskId))) {
           throw   new globalLogicEx("Task not found");
       }
       taskRepository.deleteById(taskId);
    }

    @Override
    public TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest req) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));

        if (task.getStatus() == TaskStatus.PUBLISHED && req.getStatus() == TaskStatus.DRAFT) {
            throw new globalLogicEx("Cannot revert published task to draft");
        }
        task.setStatus(req.getStatus());
        return mapToTaskResponse(task);
    }

    private TaskResponse mapToTaskResponse(Task task) {
        TaskResponse response = TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .points(task.getPoints())
                .passPoints(task.getPassPoints())
                .estimatedMinutes(task.getEstimatedMinutes())
                .status(task.getStatus().name())
                .dueAt(task.getDueAt())
                .attachmentUrl(task.getAttachmentUrl())
                .build();


        return response;
    }


    @Override
    public PageResponse<TaskResponse> getTasks(String taskName, TaskStatus status, Long msid, Pageable pageable) {

        Page<Task> tasks = taskRepository.findTasksByMentorship(msid, taskName, status,pageable);

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
    public TaskDashboardDTO getTaskDashboard(Long mentorShipId) {
        if (!(mentorShipRepository.existsById(mentorShipId))) {
            throw  new globalLogicEx("mentorShip not found");
        }
        List<Task> allTasks = taskRepository.findByWeek_Mentorship_Id(mentorShipId);

        int totalTasks = allTasks.size();
        int publishedCount = 0;
        int draftCount = 0;
        double sumAverageScores = 0.0;

        for (Task task : allTasks) {
            publishedCount += (task.getStatus() == TaskStatus.PUBLISHED ? 1 : 0);
            draftCount += (task.getStatus() == TaskStatus.DRAFT ? 1 : 0);
            sumAverageScores += calculateAverageScore(task);
        }
        double averageScore = totalTasks > 0 ? sumAverageScores / totalTasks : 0.0;

        return TaskDashboardDTO.builder()
                .totalTasks(totalTasks)
                .publishedCount(publishedCount)
                .draftCount(draftCount)
                .averageScore(averageScore)
                .build();
    }

    private double calculateAverageScore(Task task) {
        if (task.getSubmissions() == null || task.getSubmissions().isEmpty()) {
            return 0;
        }
        return task.getSubmissions().stream()
                .mapToDouble(s -> s.getFinalScore() != null ? s.getFinalScore() : 0)
                .average()
                .orElse(0.0);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskStatisticsDTO getTaskStatistics(Long taskId, Pageable pageable) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));

        Long mentorshipId = task.getWeek().getMentorship().getId();

        int totalStudents = (int) enrollmentRepository.countStudentsByMentorship(mentorshipId);

        Page<TaskSubmission> submissionsPage =
                taskSubmissionRepository.findByTask_Id(taskId, pageable);

        Page<TaskSubmissionResponse> mapped =
                submissionsPage.map(this::mapToSubmissionResponseForStats);

        PageResponse<TaskSubmissionResponse> pageResponse =
                PageResponse.<TaskSubmissionResponse>builder()
                        .content(mapped.getContent())
                        .page(mapped.getNumber())
                        .size(mapped.getSize())
                        .totalElements(mapped.getTotalElements())
                        .totalPages(mapped.getTotalPages())
                        .build();

        return TaskStatisticsDTO.builder()
                .status(task.getStatus())
                .totalStudents(totalStudents)
                .totalSubmissions((int) submissionsPage.getTotalElements())
                .pendingReview(totalStudents-submissionsPage.getTotalPages())
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
                .fileUrl(s.getFileUrl())
                .status(s.getStatus())
                .isLate(s.getIsLate())
                .rawScore(s.getRawScore())
                .finalScore(s.getFinalScore())
                .submittedAt(s.getSubmittedAt())
                .feedback(s.getFeedBack())
                .build();
    }
}
