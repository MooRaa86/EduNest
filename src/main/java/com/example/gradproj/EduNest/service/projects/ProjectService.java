package com.example.gradproj.EduNest.service.projects;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.FullProjectDashBoardDto;
import com.example.gradproj.EduNest.dto.projects.response.ProjectDashboardDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectStatisticsDTO;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectService {
    @PreAuthorize("hasRole('MENTOR')")
    ProjectResponse createProject(CreateProjectRequest req, MultipartFile file);
    ProjectResponse getProjectById(Long projectId);
    @PreAuthorize("hasRole('MENTOR')")
    ProjectResponse updateProject(long projectId, PatchProjectRequest request, MultipartFile file);
    @PreAuthorize("hasRole('MENTOR')")
    void deleteProject(Long projectId);
    @PreAuthorize("hasRole('MENTOR')")
    ProjectResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest req);
    PageResponse<ProjectResponse> getProject(String projectName, ProjectStatus status, Long msid, Pageable pageable);
    @PreAuthorize("hasRole('MENTOR')")
    ProjectDashboardDTO getProjectDashboard(Long mentorShipId);
    @PreAuthorize("hasRole('MENTOR')")
    ProjectStatisticsDTO getProjectStatistics(Long projectId, Pageable pageable);
    @PreAuthorize("hasRole('MENTOR')")
    FullProjectDashBoardDto getFullProjectDashboard(Long mentorShipId, String projectName, ProjectStatus status, Pageable pageable);
}
