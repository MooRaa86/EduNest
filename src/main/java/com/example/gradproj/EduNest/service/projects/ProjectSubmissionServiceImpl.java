package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.request.SubmitProjectRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectSubmissionServiceImpl implements  ProjectSubmissionService {
    private final ProjectRepository projectRepository;
    private final ProjectSubmissionRepository projectSubmissionRepository;
    private final StudentRepository studentRepository;
    private final TotalPointsServiceImp totalPointsService;
    private final EnrollmentRepository enrollmentRepository;

    private String getCurrentStudentEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }

        if(!(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STUDENT")))){
            throw new BadCredentialsException("you are not allowed to submit project");
        }

        return authentication.getName();
    }

    @Override
    public ProjectSubmissionResponse submit(Long projectId, SubmitProjectRequest req) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found"));

        if (project.getStatus() != ProjectStatus.PUBLISHED) {
            throw new globalLogicEx("Project is not published");
        }

        String email = getCurrentStudentEmail();

        Long studentId = studentRepository.findIdByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        boolean isEnrolled = enrollmentRepository.isStudentEnrolledForProject(projectId, studentId);

        if (!isEnrolled) {
            throw new globalLogicEx("You must enroll in this mentorship before submitting tasks.");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isLate = now.isAfter(project.getEndAt());

        Optional<ProjectSubmission> existingOpt =
                projectSubmissionRepository.findByProject_IdAndStudent_Id(projectId, studentId);

        if (existingOpt.isPresent() && isLate) {
            throw new globalLogicEx("Deadline passed. You can't resubmit because you already submitted before.");
        }

        ProjectSubmission sub = existingOpt.orElseGet(() -> {
            ProjectSubmission s = new ProjectSubmission();

            // references (proxies) بدون SELECT إضافي
            s.setProject(projectRepository.getReferenceById(projectId));
            s.setStudent(studentRepository.getReferenceById(studentId));

            s.setStatus(SubmissionStatus.SUBMITTED);
            return s;
        });

        sub.setFileUrl(req.getFileUrl());
        sub.setSubmittedAt(now);
        sub.setIsLate(isLate);
        sub.setStatus(SubmissionStatus.SUBMITTED);

        sub.setRawScore(null);
        sub.setFinalScore(null);
        sub.setFeedBack(null);
        sub.setGradedAt(null);

        ProjectSubmission saved = projectSubmissionRepository.save(sub);
        return mapToSubmissionResponse(saved);
    }

    public PageResponse<ProjectSubmissionResponse> listByProject(
            Long projectId,
            int page,
            int size
    ) {

        if (!projectRepository.existsById(projectId)) {
            throw new globalLogicEx("project not found with this id");
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<ProjectSubmission> submissionsPage =
                projectSubmissionRepository.findByProject_Id(projectId, pageable);

        List<ProjectSubmissionResponse> content =
                submissionsPage.getContent()
                        .stream()
                        .map(this::mapToSubmissionResponse)
                        .toList();

        return PageResponse.<ProjectSubmissionResponse>builder()
                .content(content)
                .page(submissionsPage.getNumber())
                .size(submissionsPage.getSize())
                .totalElements(submissionsPage.getTotalElements())
                .totalPages(submissionsPage.getTotalPages())
                .build();
    }

    @Override
    public ProjectSubmissionResponse gradeProject(Long submissionId, GradeProjectSubmissionRequest req) {

        ProjectSubmission sub = projectSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        Project project = sub.getProject();

        if (req.getScore() > project.getPoints()) {
            throw new globalLogicEx(
                    "score must be less than or equal to project points " + project.getPoints());
        }

        sub.setRawScore(project.getPoints());
        sub.setFeedBack(req.getFeedback());
        sub.setFinalScore(req.getScore());
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setGradedAt(LocalDateTime.now());

        MentorShip mentorship = project.getWeek().getMentorship();

        int newScore = sub.getFinalScore();
        int oldApplied = (sub.getPointsApplied() == null) ? 0 : sub.getPointsApplied();
        int delta = newScore - oldApplied;

        if (delta != 0) {
            totalPointsService.applyDelta(sub.getStudent(), mentorship, delta);
        }

        sub.setPointsApplied(newScore);

        projectSubmissionRepository.save(sub);

        return mapToSubmissionResponse(sub);
    }


    private ProjectSubmissionResponse mapToSubmissionResponse(ProjectSubmission s) {
        return ProjectSubmissionResponse.builder()
                .submissionId(s.getId())
                .projectId(s.getProject().getId())
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
