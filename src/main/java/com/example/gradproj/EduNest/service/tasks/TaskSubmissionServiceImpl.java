package com.example.gradproj.EduNest.service.tasks;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskSubmissionServiceImpl implements TaskSubmissionService {
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final EnrollmentRepository enrollmentRepository;
    private final MentorRepository mentorRepository;
    private final TaskFileStorageService fileStorageService;
    private final NotificationService notificationService;

    private String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    private Long getCurrentStudentId() {
        return studentRepository.findIdByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
    }

    private Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    private void validateMentorOwnsTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));
        Long mentorId = task.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this task");
        }
    }

    private TaskSubmission validateMentorOwnsSubmission(Long submissionId) {
        TaskSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        Long mentorId = sub.getTask().getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to grade this submission");
        }
        return sub;
    }

    @Override
    public TaskSubmissionResponse submit(Long taskId, MultipartFile file, String fileUrl) {
        if ((file == null || file.isEmpty()) && (fileUrl == null || fileUrl.isBlank())) {
            throw new globalLogicEx("Either file upload or file URL must be provided");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new globalLogicEx("Task not found"));

        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new globalLogicEx("Task is not published");
        }

        Long studentId = getCurrentStudentId();

        boolean isEnrolled = enrollmentRepository.isStudentEnrolledForTask(taskId, studentId);

        if (!isEnrolled) {
            throw new globalLogicEx("You must enroll in this mentorship before submitting tasks.");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isLate = now.isAfter(task.getDueAt());

        Optional<TaskSubmission> existingOpt =
                submissionRepository.findByTask_IdAndStudent_Id(taskId, studentId);

        if (isLate && existingOpt.isPresent()) {
            throw new globalLogicEx("Deadline passed. You can't resubmit because you already submitted before.");
        }

        String uploadedPath = null;
        if (file != null && !file.isEmpty()) {
            uploadedPath = fileStorageService.saveFile("submissions", "task", taskId, studentId, file);
        }

        TaskSubmission sub = existingOpt.orElseGet(() -> {
            TaskSubmission s = new TaskSubmission();
            s.setTask(taskRepository.getReferenceById(taskId));
            s.setStudent(studentRepository.getReferenceById(studentId));
            s.setStatus(SubmissionStatus.SUBMITTED);
            return s;
        });

        if (fileUrl != null && !fileUrl.isBlank())
            sub.setFileUrl(fileUrl);

        if (uploadedPath != null)
            sub.setUploadedFilePath(uploadedPath);

        sub.setSubmittedAt(now);
        sub.setIsLate(isLate);
        sub.setStatus(SubmissionStatus.SUBMITTED);

        sub.setRawScore(null);
        sub.setFinalScore(null);
        sub.setFeedBack(null);
        sub.setGradedAt(null);

        TaskSubmission saved = submissionRepository.save(sub);

        // Notify the mentor that a student submitted a task
        String mentorEmail = task.getWeek().getMentorship().getMentor().getEmail();
        String studentName = saved.getStudent().getFirstName() + " " + saved.getStudent().getLastName();
        notificationService.sendToUserByEmail(
                mentorEmail,
                "New Task Submission",
                studentName + " submitted task \"" + task.getTitle() + "\"",
                NotificationType.TASK
        );

        return mapToSubmissionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSubmissionResponse> listByTask(Long taskId, int page, int size) {
        validateMentorOwnsTask(taskId);

        Pageable pageable = PageRequest.of(page, size);

        Page<TaskSubmission> submissionsPage =
                submissionRepository.findByTask_Id(taskId, pageable);

        List<TaskSubmissionResponse> content =
                submissionsPage.getContent()
                        .stream()
                        .map(this::mapToSubmissionResponse)
                        .toList();

        return PageResponse.<TaskSubmissionResponse>builder()
                .content(content)
                .page(submissionsPage.getNumber())
                .size(submissionsPage.getSize())
                .totalElements(submissionsPage.getTotalElements())
                .totalPages(submissionsPage.getTotalPages())
                .build();
    }

    @Override
    public TaskSubmissionResponse grade(Long submissionId, GradeTaskSubmissionRequest req) {
        TaskSubmission sub = validateMentorOwnsSubmission(submissionId);

        Task task = sub.getTask();

        if (req.getScore() > task.getPoints()) {
            throw new globalLogicEx("score must be less than or equal to task points " + task.getPoints());
        }

        sub.setRawScore(task.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());

        MentorShip mentorship = task.getWeek().getMentorship();
        int newScore = sub.getFinalScore();
        int oldApplied = (sub.getPointsApplied() == null) ? 0 : sub.getPointsApplied();
        int delta = newScore - oldApplied;

        if (delta != 0) {
            totalPointsService.applyDelta(sub.getStudent(), mentorship, delta);
        }

        sub.setPointsApplied(newScore);

        submissionRepository.save(sub);

        // Notify the student that their task has been graded
        notificationService.sendToUserByEmail(
                sub.getStudent().getEmail(),
                "Task Graded",
                "Your task \"" + task.getTitle() + "\" has been graded. Score: " + sub.getFinalScore() + "/" + task.getPoints(),
                NotificationType.TASK
        );

        return mapToSubmissionResponse(sub);
    }


    private TaskSubmissionResponse mapToSubmissionResponse(TaskSubmission s) {
        return TaskSubmissionResponse.builder()
                .submissionId(s.getId())
                .taskId(s.getTask().getId())
                .studentId(s.getStudent().getId())
                .studentFullName(s.getStudent().getFirstName() + " " + s.getStudent().getLastName())
                .fileUrl(s.getFileUrl())
                .uploadedFilePath(s.getUploadedFilePath())
                .status(SubmissionStatus.valueOf(s.getStatus().name()))
                .isLate(s.getIsLate())
                .rawScore(s.getRawScore())
                .finalScore(s.getFinalScore())
                .submittedAt(s.getSubmittedAt())
                .feedback(s.getFeedBack())
                .build();
    }

}
