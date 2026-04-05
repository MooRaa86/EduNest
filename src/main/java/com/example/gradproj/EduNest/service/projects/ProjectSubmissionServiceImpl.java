package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectWithSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectWithSubmissionProjection;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import com.example.gradproj.EduNest.service.tasks.TaskFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
    private final TaskFileStorageService fileStorageService;

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
        boolean isLate = now.isAfter(project.getEndAt());

        Optional<ProjectSubmission> existingOpt =
                projectSubmissionRepository.findByProject_IdAndStudent_Id(projectId, studentId);

        if (existingOpt.isPresent() && isLate) {
            throw new globalLogicEx("Deadline passed. You can't resubmit because you already submitted before.");
        }

        String uploadedPath = null;
        if (file != null && !file.isEmpty()) {
            uploadedPath = fileStorageService.saveFile("project", projectId, studentId, file);
        }

        ProjectSubmission sub = existingOpt.orElseGet(() -> {
            ProjectSubmission s = new ProjectSubmission();
            s.setProject(projectRepository.getReferenceById(projectId));
            s.setStudent(studentRepository.getReferenceById(studentId));
            s.setStatus(SubmissionStatus.SUBMITTED);
            return s;
        });

        sub.setFileUrl(fileUrl);
        sub.setUploadedFilePath(uploadedPath);
        sub.setSubmittedAt(now);
        sub.setIsLate(isLate);
        sub.setStatus(SubmissionStatus.SUBMITTED);
        sub.setRawScore(null);
        sub.setFinalScore(null);
        sub.setFeedBack(null);
        sub.setGradedAt(null);

        return mapToSubmissionResponse(projectSubmissionRepository.save(sub));
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
        Project project = sub.getProject();

        if (req.getScore() > project.getPoints()) {
            throw new globalLogicEx("score must be less than or equal to project points " + project.getPoints());
        }

        sub.setRawScore(project.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());

        MentorShip mentorship = project.getWeek().getMentorship();
        int delta = sub.getFinalScore() - (sub.getPointsApplied() == null ? 0 : sub.getPointsApplied());
        if (delta != 0) totalPointsService.applyDelta(sub.getStudent(), mentorship, delta);
        sub.setPointsApplied(sub.getFinalScore());

        projectSubmissionRepository.save(sub);
        return mapToSubmissionResponse(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWithSubmissionResponse getProjectWithSubmission(Long projectId, String email) {
        validateEnrolled(projectId, email);
        ProjectWithSubmissionProjection p = projectSubmissionRepository.findProjectWithSubmission(projectId, email);
        if (p == null) throw new globalLogicEx("Project not found");
        return ProjectWithSubmissionResponse.builder()
                .projectId(p.getProjectId())
                .title(p.getTitle())
                .brief(p.getBrief())
                .descriptionUrl(p.getDescriptionUrl())
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
                .submissionId(s.getId())
                .projectId(s.getProject().getId())
                .studentId(s.getStudent().getId())
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
}
