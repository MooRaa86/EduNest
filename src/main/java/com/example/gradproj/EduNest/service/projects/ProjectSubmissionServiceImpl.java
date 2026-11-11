package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.config.learning.LateSubmissionProperties;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectSubmissionAuthProjection;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectWithSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.LegacySubmissionAuditRequiredException;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectWithSubmissionProjection;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.ContentReadGuard;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectSubmissionServiceImpl implements ProjectSubmissionService {
    private final ProjectRepository projectRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final StudentRepository studentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final EnrollmentRepository enrollmentRepository;
    private final MentorRepository mentorRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ContentReadGuard contentReadGuard;
    private final WeekRepository weekRepository;
    private final EntityManager entityManager;
    private final LateSubmissionProperties lateSubmissionProperties;

    private void validateEnrolled(Long projectId, String email) {
        if (!enrollmentRepository.isStudentEnrolledForProjectByEmail(projectId, email)) {
            throw new globalLogicEx("You are not enrolled in this mentorship");
        }
    }

    private ProjectSubmission validateMentorOwnsSubmission(Long submissionId, String email) {
        ProjectSubmission sub = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        if (!sub.getProject().getWeek().getMentorship().getMentor().getEmail().equals(email)) {
            throw new AccessDeniedException("You are not authorized to grade this submission");
        }
        return sub;
    }

    @Override
    public ProjectSubmissionResponse submit(Long projectId, MultipartFile file, String fileUrl, String email) {
        if ((file == null || file.isEmpty()) && (fileUrl == null || fileUrl.isBlank())) {
            throw new globalLogicEx("Either file upload or file URL must be provided");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("project not found"));

        if (project.getStatus() != ProjectStatus.PUBLISHED) {
            throw new globalLogicEx("Project is not published");
        }

        validateEnrolled(projectId, email);

        Long studentId = studentRepository.findIdByEmail(email)
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        LocalDateTime now = LocalDateTime.now();
        /*
         * EB-53 / D-08 — late policy: a first submission within the configured
         * grace (app.submission.late-grace-minutes, default 10) after the
         * project deadline is accepted with isLate=true; grading stays
         * mentor-assigned (no automatic deduction). Beyond the grace the first
         * submission is rejected. Late resubmissions stay rejected. A null
         * deadline never blocks submission.
         *
         * The grace boundary is compared on exact timestamps so that a
         * submission even one second past deadline + grace is rejected.
         */
        boolean isLate = project.getEndAt() != null && now.isAfter(project.getEndAt());

        Optional<ProjectSubmission> existingOpt =
                projectSubmissionRepository.findByProject_IdAndStudent_Id(projectId, studentId);

        if (existingOpt.isPresent() && isLate) {
            throw new globalLogicEx("Deadline passed. You can't resubmit because you already submitted before.");
        }

        if (isLate && now.isAfter(project.getEndAt().plusMinutes(lateSubmissionProperties.getLateGraceMinutes()))) {
            throw new globalLogicEx("Submission deadline plus grace period has passed. This project no longer accepts first submissions.");
        }

        String uploadedPath = null;
        String oldPath = null;
        if (file != null && !file.isEmpty()) {
            if (existingOpt.isPresent()) {
                oldPath = existingOpt.get().getUploadedFilePath();
            }
            uploadedPath = fileStorageService.saveFile("submissions", "project", projectId, studentId, file);
            if (oldPath != null && !oldPath.isBlank()) {
                fileStorageService.deleteFileAfterCommit(oldPath);
            }
        }

        ProjectSubmission sub = existingOpt.orElseGet(() -> {
            ProjectSubmission s = new ProjectSubmission();
            s.setProject(projectRepository.getReferenceById(projectId));
            s.setStudent(studentRepository.getReferenceById(studentId));
            s.setStatus(SubmissionStatus.SUBMITTED);
            s.setPointsApplied(0);
            return s;
        });

        if (fileUrl != null && !fileUrl.isBlank()) {sub.setFileUrl(fileUrl);}
        if (uploadedPath != null) {sub.setUploadedFilePath(uploadedPath);}
        sub.setSubmittedAt(now);
        sub.setIsLate(isLate);
        sub.setStatus(SubmissionStatus.SUBMITTED);
        sub.setRawScore(null);
        sub.setFinalScore(null);
        sub.setFeedBack(null);
        sub.setGradedAt(null);

        ProjectSubmission savedSub = projectSubmissionRepository.save(sub);

        String mentorEmail = project.getWeek().getMentorship().getMentor().getEmail();
        String studentName = savedSub.getStudent().getFirstName() + " " + savedSub.getStudent().getLastName();
        notificationService.sendToUserByEmail(
                mentorEmail,
                "New Project Submission",
                studentName + " submitted project \"" + project.getTitle() + "\"",
                NotificationType.PROJECT
        );

        return mapToSubmissionResponse(savedSub);
    }

    @Override
    public PageResponse<ProjectSubmissionResponse> listByProject(Long projectId, int page, int size, String email) {
        if (!mentorRepository.findByEmail(email)
                .map(m -> projectRepository.findById(projectId)
                        .map(p -> p.getWeek().getMentorship().getMentor().getEmail().equals(email))
                        .orElse(false))
                .orElse(false)) {
            throw new AccessDeniedException("You are not authorized to access this project");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ProjectSubmission> submissionsPage = projectSubmissionRepository.findByProject_Id(projectId, pageable);
        List<ProjectSubmissionResponse> content = submissionsPage.getContent().stream()
                .map(this::mapToSubmissionResponse).toList();

        return PageResponse.<ProjectSubmissionResponse>builder()
                .content(content)
                .page(submissionsPage.getNumber())
                .size(submissionsPage.getSize())
                .totalElements(submissionsPage.getTotalElements())
                .totalPages(submissionsPage.getTotalPages())
                .build();
    }

    @Override
    public ProjectSubmissionResponse gradeProject(Long submissionId, GradeProjectSubmissionRequest req, String email) {
        ProjectSubmission sub = validateMentorOwnsSubmission(submissionId, email);

        // EB-51 — L1 -> L2 -> L3 ordered locking: week -> project -> submission
        Long weekId = sub.getProject().getWeek().getId();
        Long projectId = sub.getProject().getId();
        weekRepository.findForUpdateById(weekId)
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        Project project = projectRepository.findForUpdateById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));
        sub = projectSubmissionRepository.findForUpdateById(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        if (entityManager != null) {
            entityManager.refresh(sub);
        }

        // EB-51 D-09a: Strict integrity gate - reject grading if points provenance is null
        if (sub.getPointsApplied() == null) {
            throw new LegacySubmissionAuditRequiredException(
                    "Project submission " + submissionId + " lacks recorded points provenance. Grading requires administrative reconciliation.");
        }

        if (req.getScore() > project.getPoints()) {
            throw new globalLogicEx("score must be less than or equal to project points " + project.getPoints());
        }

        sub.setRawScore(project.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());

        MentorShip mentorship = project.getWeek().getMentorship();
        int delta = sub.getFinalScore() - sub.getPointsApplied();
        if (delta != 0) totalPointsService.applyDelta(sub.getStudent(), mentorship, delta);
        sub.setPointsApplied(sub.getFinalScore());

        projectSubmissionRepository.save(sub);

        // Notify the student that their project has been graded
        notificationService.sendToUserByEmail(
                sub.getStudent().getEmail(),
                "Project Graded",
                "Your project \"" + project.getTitle() + "\" has been graded. Score: " + sub.getFinalScore() + "/" + project.getPoints(),
                NotificationType.PROJECT
        );

        return mapToSubmissionResponse(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWithSubmissionResponse getProjectWithSubmission(Long projectId, String email) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new AccessDeniedException("Content unavailable"));
        contentReadGuard.requireMentorship(project.getWeek().getMentorship().getId());
        contentReadGuard.requireVisibleToStudent(project.getWeek().getMentorship().getId(), project.getStatus() == ProjectStatus.PUBLISHED || project.getStatus() == ProjectStatus.CLOSED);
        ProjectWithSubmissionProjection p = projectSubmissionRepository.findProjectWithSubmission(projectId, contentReadGuard.currentEmail());
        if (p == null) throw new globalLogicEx("Project not found");
        return ProjectWithSubmissionResponse.builder()
                .projectId(p.getProjectId())
                .title(p.getTitle())
                .brief(p.getBrief())
                .descriptionUrl(p.getDescriptionUrl())
                .attachmentPath(p.getAttachmentPath())
                .points(p.getPoints())
                .startAt(p.getStartAt())
                .endAt(p.getEndAt())
                .goal(p.getGoal())
                .submissionId(p.getSubmissionId())
                .submissionStatus(p.getSubmissionStatus())
                .score(p.getFinalScore())
                .totalPoints(p.getTotalPoints())
                .fileUrl(p.getFileUrl())
                .uploadedFilePath(p.getUploadedFilePath())
                .feedback(p.getFeedback())
                .mentorId(p.getMentorId())
                .mentorName(p.getMentorName())
                .mentorPhoto(p.getMentorPhoto())
                .build();
    }

    private ProjectSubmissionResponse mapToSubmissionResponse(ProjectSubmission s) {
        return ProjectSubmissionResponse.builder()
                .submissionId(s.getPublicId())
                .projectId(s.getProject().getPublicId())
                .studentId(s.getStudent().getPublicId())
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
    public ProjectSubmissionResponse submit(UUID projectId, MultipartFile file, String fileUrl, String email) {
        Long internalId = projectRepository.findIdByPublicId(projectId)
                .orElseThrow(() -> new globalLogicEx("project not found"));
        return submit(internalId, file, fileUrl, email);
    }

    @Override
    public PageResponse<ProjectSubmissionResponse> listByProject(UUID projectId, int page, int size, String email) {
        Long internalId = projectRepository.findIdByPublicId(projectId)
                .orElseThrow(() -> new globalLogicEx("project not found"));
        return listByProject(internalId, page, size, email);
    }

    @Override
    public ProjectSubmissionResponse gradeProject(UUID submissionId, GradeProjectSubmissionRequest req, String email) {
        Long internalId = projectSubmissionRepository.findIdByPublicId(submissionId)
                .orElseThrow(() -> new globalLogicEx("Submission not found"));
        return gradeProject(internalId, req, email);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWithSubmissionResponse getProjectWithSubmission(UUID projectId, String email) {
        Long internalId = projectRepository.findIdByPublicId(projectId)
                .orElseThrow(() -> new AccessDeniedException("Content unavailable"));
        return getProjectWithSubmission(internalId, email);
    }
}
