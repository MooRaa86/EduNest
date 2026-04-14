package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.FullProjectDashBoardDto;
import com.example.gradproj.EduNest.dto.projects.response.ProjectDashboardDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectStatisticsDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectSubmissionResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectWithStatsResponse;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectSubmissionRepository;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectWithStatsProjection;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.tasks.TaskFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService{
    private final ProjectRepository projectRepository;
    private final MentorShipRepository mentorShipRepository;
    private final WeekRepository weekRepository;
    private final ProjectSubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MentorRepository mentorRepository;
    private final TaskFileStorageService fileStorageService;

    private String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    private Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    private void validateMentorshipOwnership(Long mentorShipId) {
        Long mentorId = mentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("mentorShip not found"))
                .getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this mentorship");
        }
    }

    private Project validateMentorOwnershipAndGetProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));
        Long mentorId = project.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this project");
        }
        return project;
    }


    @Override
    public ProjectResponse createProject(CreateProjectRequest req, MultipartFile file) {
        Week week=weekRepository.findById(req.getWeekId()).orElseThrow(() -> new globalLogicEx("week not found"));
        Long mentorId = week.getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to create projects for this mentorship");
        }
        if (req.getEndAt().isBefore(req.getStartAt())) {
            throw new globalLogicEx("endAt must be after startAt");
        }

        String uploadedPath = null;
        if (file != null && !file.isEmpty()) {
            uploadedPath = fileStorageService.saveFile("project-attachment", "project", week.getMentorship().getId(), mentorId, file);
        }

        Project project = Project.builder()
                .title(req.getTitle())
                .goal(req.getGoal())
                .brief(req.getBrief())
                .descriptionUrl(req.getDescriptionUrl())
                .uploadedAttachmentPath(uploadedPath)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .points(req.getPoints())
                .status(req.getStatus())
                .week(week)
                .build();
        projectRepository.save(project);
        return mapToProjectResponse(project);
    }

    @Override
    public ProjectResponse getProjectById(Long projectId) {
        Project project=projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("project not found"));
        return  mapToProjectResponse(project);
    }

    @Override
    public ProjectResponse updateProject(long projectId, PatchProjectRequest req, MultipartFile file) {
        Project project = validateMentorOwnershipAndGetProject(projectId);

        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new globalLogicEx("Cannot update closed project");
        }

        if (req != null) {
            if (req.getTitle() != null) project.setTitle(req.getTitle());
            if (req.getGoal() != null) project.setGoal(req.getGoal());
            if (req.getBrief() != null) project.setBrief(req.getBrief());
            if (req.getDescriptionUrl() != null) project.setDescriptionUrl(req.getDescriptionUrl());
            if (req.getPoints() != null) project.setPoints(req.getPoints());
            if (req.getStatus()!= null) project.setStatus(req.getStatus());

            LocalDateTime start = req.getStartAt() != null ? req.getStartAt() : project.getStartAt();
            LocalDateTime end   = req.getEndAt()   != null ? req.getEndAt()   : project.getEndAt();

            if (end.isBefore(start)) {
                throw new globalLogicEx("endAt must be after startAt");
            }

            if (req.getStartAt() != null) project.setStartAt(req.getStartAt());
            if (req.getEndAt() != null) project.setEndAt(req.getEndAt());
        }

        if (file != null && !file.isEmpty()) {
            Long mentorId = getCurrentMentorId();
            Long mentorshipId = project.getWeek().getMentorship().getId();
            String uploadedPath = fileStorageService.saveFile("project-attachment", "project", mentorshipId, mentorId, file);
            project.setUploadedAttachmentPath(uploadedPath);
        }

        return mapToProjectResponse(project);
    }

    @Override
    public void deleteProject(Long projectId) {
        validateMentorOwnershipAndGetProject(projectId);
        projectRepository.deleteById(projectId);
    }

    @Override
    public ProjectResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest req) {
        Project project = validateMentorOwnershipAndGetProject(projectId);

        if ((project.getStatus() == ProjectStatus.PUBLISHED) && (req.getStatus() == ProjectStatus.DRAFT)) {
            throw new globalLogicEx("Cannot revert published project to draft");
        }
        project.setStatus(req.getStatus());
        return mapToProjectResponse(project);
    }

    @Override
    public PageResponse<ProjectWithStatsResponse> getProject(String projectName, ProjectStatus status, Long msid, Pageable pageable) {
        Page<ProjectWithStatsProjection> projects =
                projectRepository.findProjectsWithStatsByMentorship(msid, projectName, status, pageable);

        List<ProjectWithStatsResponse> responses = projects.getContent()
                .stream()
                .map(p -> ProjectWithStatsResponse.builder()
                        .project(mapProjectionToProjectResponse(p))
                        .totalStudents(p.getTotalStudents())
                        .submissionsCount(p.getSubmissionsCount())
                        .build())
                .toList();

        return PageResponse.<ProjectWithStatsResponse>builder()
                .content(responses)
                .page(projects.getNumber())
                .size(projects.getSize())
                .totalElements(projects.getTotalElements())
                .totalPages(projects.getTotalPages())
                .build();
    }


    @Override
    public ProjectDashboardDTO getProjectDashboard(Long mentorShipId) {
        validateMentorshipOwnership(mentorShipId);
        var stats = projectRepository.getDashboardStats(mentorShipId);
        return ProjectDashboardDTO.builder()
                .totalProjects(stats.getTotalProjects() != null ? stats.getTotalProjects().intValue() : 0)
                .publishedCount(stats.getPublishedCount() != null ? stats.getPublishedCount().intValue() : 0)
                .draftCount(stats.getDraftCount() != null ? stats.getDraftCount().intValue() : 0)
                .averageScore(stats.getAverageScore() != null ? stats.getAverageScore() : 0.0)
                .build();
    }


    private ProjectResponse mapProjectionToProjectResponse(ProjectWithStatsProjection p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .goal(p.getGoal())
                .brief(p.getBrief())
                .descriptionUrl(p.getDescriptionUrl())
                .uploadedAttachmentPath(p.getUploadedAttachmentPath())
                .startAt(p.getStartAt())
                .endAt(p.getEndAt())
                .points(p.getPoints())
                .status(p.getStatus().name())
                .weekId(p.getWeekId())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ProjectResponse mapToProjectResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .goal(project.getGoal())
                .brief(project.getBrief())
                .descriptionUrl(project.getDescriptionUrl())
                .uploadedAttachmentPath(project.getUploadedAttachmentPath())
                .startAt(project.getStartAt())
                .endAt(project.getEndAt())
                .points(project.getPoints())
                .status(project.getStatus().name())
                .weekId(project.getWeek().getId())
                .createdAt(project.getCreatedAt())
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public ProjectStatisticsDTO getProjectStatistics(Long projectId, Pageable pageable) {
        Project project = validateMentorOwnershipAndGetProject(projectId);

        Long mentorshipId = project.getWeek().getMentorship().getId();

        int totalStudents = (int) enrollmentRepository.countStudentsByMentorship(mentorshipId);

        Page<ProjectSubmission> submissionsPage =
                submissionRepository.findByProject_Id(projectId, pageable);

        int totalSubmissions = (int) submissionsPage.getTotalElements();


        int pendingReview = totalStudents-totalSubmissions;

        Page<ProjectSubmissionResponse> mapped =
                submissionsPage.map(this::mapToProjectSubmissionResponse);

        PageResponse<ProjectSubmissionResponse> pageResponse =
                PageResponse.<ProjectSubmissionResponse>builder()
                        .content(mapped.getContent())
                        .page(mapped.getNumber())
                        .size(mapped.getSize())
                        .totalElements(mapped.getTotalElements())
                        .totalPages(mapped.getTotalPages())
                        .build();

        return ProjectStatisticsDTO.builder()
                .status(project.getStatus())
                .projectTitle(project.getTitle())
                .totalStudents(totalStudents)
                .totalSubmissions(totalSubmissions)
                .pendingReview(pendingReview)
                .createdAt(project.getCreatedAt())
                .deadLine(project.getEndAt())
                .totalPoints(project.getPoints())
                .taskSubmissionResponsePageResponse(pageResponse)
                .build();
    }
    private ProjectSubmissionResponse mapToProjectSubmissionResponse(ProjectSubmission s) {
        return ProjectSubmissionResponse.builder()
                .submissionId(s.getId())
                .projectId(s.getProject().getId())
                .studentId(s.getStudent().getId())
                .studentFullName(s.getStudent().getFirstName() + " " + s.getStudent().getLastName())
                .fileUrl(s.getFileUrl())
                .submittedAt(s.getSubmittedAt())
                .isLate(s.getIsLate())
                .status(s.getStatus())
                .rawScore(s.getRawScore())
                .finalScore(s.getFinalScore())
                .feedback(s.getFeedBack())
                .build();
    }

    @Override
    public FullProjectDashBoardDto getFullProjectDashboard(Long mentorShipId, String projectName, ProjectStatus status, Pageable pageable) {
        validateMentorshipOwnership(mentorShipId);
        ProjectDashboardDTO dashboard = getProjectDashboard(mentorShipId);
        PageResponse<ProjectWithStatsResponse> projects = getProject(projectName, status, mentorShipId, pageable);
        
        return FullProjectDashBoardDto.builder()
                .projectDashboardDTO(dashboard)
                .projectResponsePageResponse(projects)
                .build();
    }
}
