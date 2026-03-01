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

public interface ProjectService {
    ProjectResponse createProject(CreateProjectRequest req);
    ProjectResponse getProjectById(Long projectId);
    ProjectResponse updateProject(long projectId, PatchProjectRequest request);
    void deleteProject(Long projectId);
    ProjectResponse updateProjectStatus(Long projectId, UpdateProjectStatusRequest req);
    PageResponse<ProjectResponse> getProject(String projectName, ProjectStatus status, Long msid, Pageable pageable);
    ProjectDashboardDTO getProjectDashboard(Long mentorShipId);
    ProjectStatisticsDTO getProjectStatistics(Long projectId, Pageable pageable);
    FullProjectDashBoardDto getFullProjectDashboard(Long mentorShipId, String projectName, ProjectStatus status, Pageable pageable);
}
