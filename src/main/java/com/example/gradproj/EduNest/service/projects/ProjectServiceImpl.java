package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectDashboardDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ProjectServiceImpl implements ProjectService{
    private final ProjectRepository projectRepository;
    private final mentorShipRepository mentorShipRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository,mentorShipRepository mentorShipRepository) {
        this.projectRepository = projectRepository;
        this.mentorShipRepository=mentorShipRepository;
    }

    @Override
    public ProjectResponse createProject(CreateProjectRequest req) {
        mentorShipE mentorship = mentorShipRepository.findById(req.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        if (req.getEndAt().isBefore(req.getStartAt())) {
            throw new globalLogicEx("endAt must be after startAt");
        }

        Project project = Project.builder()
                .title(req.getTitle())
                .goal(req.getGoal())
//                .difficulty(req.getDifficulty())
                .brief(req.getBrief())
                .descriptionUrl(req.getDescriptionUrl())
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .points(req.getPoints())
                .status(req.getStatus())
                .mentorship(mentorship)
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
    public ProjectResponse updateProject(long projectId, PatchProjectRequest req) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));

        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new globalLogicEx("Cannot update closed project");
        }

        if (req.getTitle() != null) project.setTitle(req.getTitle());
        if (req.getGoal() != null) project.setGoal(req.getGoal());
//        if (req.getDifficulty() != null) project.setDifficulty(req.getDifficulty());
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

        return mapToProjectResponse(project);
    }

    @Override
    public void deleteProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(()->new globalLogicEx("project not found"));
        projectRepository.delete(project);

    }

    @Override
    public ProjectResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest req) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("Project not found"));

        if ((project.getStatus() == ProjectStatus.PUBLISHED) && (req.getStatus() == ProjectStatus.DRAFT)) {
            throw new globalLogicEx("Cannot revert published project to draft");
        }
        project.setStatus(req.getStatus());
        return mapToProjectResponse(project);
    }

    @Override
    public PageResponse<ProjectResponse> getProject(String projectName, ProjectStatus status, Long msid, Pageable pageable) {
        Page<Project> projects =
                projectRepository.findProjectsByMentorship(
                        msid, projectName, status, pageable);

        List<ProjectResponse> responses = projects.getContent()
                .stream()
                .map(this::mapToProjectResponse)
                .toList();

        return PageResponse.<ProjectResponse>builder()
                .content(responses)
                .page(projects.getNumber())
                .size(projects.getSize())
                .totalElements(projects.getTotalElements())
                .totalPages(projects.getTotalPages())
                .build();
    }


    @Override
    public ProjectDashboardDTO getProjectDashboard(Long mentorShipId) {
        mentorShipE mentorShip = mentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        List<Project> allProjects = projectRepository.findByMentorshipId(mentorShipId);

        int totalProjects = allProjects.size();
        int publishedCount = 0;
        int draftCount = 0;
        double sumAverageScores = 0.0;

        for (Project project : allProjects) {
            publishedCount += (project.getStatus() == ProjectStatus.PUBLISHED ? 1 : 0);
            draftCount += (project.getStatus() == ProjectStatus.DRAFT ? 1 : 0);
            sumAverageScores += calculateAverageScore(project);
        }
        double averageScore = totalProjects > 0 ? sumAverageScores / totalProjects : 0.0;

        return ProjectDashboardDTO.builder()
                .totalTasks(totalProjects)
                .publishedCount(publishedCount)
                .draftCount(draftCount)
                .averageScore(averageScore)
                .build();
    }
    private double calculateAverageScore(Project project) {
        if (project.getSubmissions() == null || project.getSubmissions().isEmpty()) {
            return 0;
        }
        return project.getSubmissions().stream()
                .mapToDouble(s -> s.getFinalScore() != null ? s.getFinalScore() : 0)
                .average()
                .orElse(0.0);
    }


    private ProjectResponse mapToProjectResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .goal(project.getGoal())
//                .difficulty(project.getDifficulty().name())
                .brief(project.getBrief())
                .descriptionUrl(project.getDescriptionUrl())
                .startAt(project.getStartAt())
                .endAt(project.getEndAt())
                .points(project.getPoints())
                .status(project.getStatus().name())
                .mentorshipId(project.getMentorship().getId())
                .createdAt(project.getCreatedAt())
                .build();
    }
}
