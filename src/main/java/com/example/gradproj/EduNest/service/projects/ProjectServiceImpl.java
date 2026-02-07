package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectDashboardDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.dto.tasks.response.TaskDashboardDTO;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
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
        if (req.getPassPoints()> req.getPoints()){
            throw new globalLogicEx("passPoints must be less than or equal to points");
        }
        mentorShipE mentorship = mentorShipRepository.findById(req.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));


        Project project= Project.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .points(req.getPoints())
                .passPoints(req.getPassPoints())
                .estimatedMinutes(req.getEstimatedMinutes())
                .dueAt(req.getDueAt())
                .attachmentUrl(req.getAttachmentUrl())
                .status(ProjectStatus.DRAFT)
                .mentorship(mentorship)
                .build();
        Project saved=projectRepository.save(project);

        return mapToProjectResponse(saved);
    }

    @Override
    public ProjectResponse getProjectById(Long projectId) {
        Project project=projectRepository.findById(projectId)
                .orElseThrow(() -> new globalLogicEx("project not found"));
        return  mapToProjectResponse(project);
    }

    @Override
    public ProjectResponse updateProject(long projectId, PatchProjectRequest request) {
        Project project=projectRepository.findById(projectId).orElseThrow(()->new IllegalArgumentException("Project not found"));
        if (project.getStatus() == ProjectStatus.CLOSED){
            throw new globalLogicEx("cannot update closed task");
        }
        if (request.getTitle() !=null)project.setTitle(request.getTitle());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getPoints() != null) project.setPoints(request.getPoints());
        if (request.getPassPoints() != null) project.setPassPoints(request.getPassPoints());
        if (request.getEstimatedMinutes() != null) project.setEstimatedMinutes(request.getEstimatedMinutes());
        if (request.getAttachmentUrl() != null) project.setAttachmentUrl(request.getAttachmentUrl());
        if (request.getDueAt() != null){
            if (request.getDueAt().isBefore(LocalDateTime.now())){
                throw new globalLogicEx("dueAt must be in the future ");
            }
            project.setDueAt(request.getDueAt());

        }
        if (project.getPassPoints()>project.getPoints()){
            throw  new globalLogicEx("Pass points must be less than or equal to points.");
        }
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

        Page<Project> projects = projectRepository.findProjectsByMentorship(msid, projectName, status, pageable);

        List<ProjectResponse> projectDTOs = projects.getContent().stream()
                .map(project -> ProjectResponse.builder()
                        .id(project.getId())
                        .title(project.getTitle())
                        .description(project.getDescription())
                        .points(project.getPoints())
                        .passPoints(project.getPassPoints())
                        .estimatedMinutes(project.getEstimatedMinutes())
                        .status(String.valueOf(project.getStatus()))
                        .dueAt(project.getDueAt())
                        .attachmentUrl(project.getAttachmentUrl())
                        .build())
                .toList();

        return PageResponse.<ProjectResponse>builder()
                .content(projectDTOs)
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
        ProjectResponse res = new ProjectResponse();
        res.setId(project.getId());
        res.setTitle(project.getTitle());
        res.setDescription(project.getDescription());
        res.setPoints(project.getPoints());
        res.setPassPoints(project.getPassPoints());
        res.setEstimatedMinutes(project.getEstimatedMinutes());
        res.setStatus(project.getStatus().name());
        res.setDueAt(project.getDueAt());
        res.setAttachmentUrl(project.getAttachmentUrl());

        return res;
    }
}
