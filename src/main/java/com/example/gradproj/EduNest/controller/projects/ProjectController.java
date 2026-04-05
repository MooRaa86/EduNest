package com.example.gradproj.EduNest.controller.projects;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.FullProjectDashBoardDto;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.dto.projects.response.ProjectStatisticsDTO;
import com.example.gradproj.EduNest.dto.projects.response.ProjectWithStatsResponse;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.service.projects.ProjectServiceImpl;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/project")
@Tag(
        name = "project",
        description = "APIs for managing projects (create, update, delete, filter, dashboard"
)
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectServiceImpl projectService;
    private final ProjectSubmissionService submissionService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create project with optional file attachment")
    public ResponseEntity<SimpleResponse> create(
            @RequestPart("req") String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {
        CreateProjectRequest req = objectMapper.readValue(reqJson, CreateProjectRequest.class);
        ProjectResponse created = projectService.createProject(req, file);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project created successfully");
        response.addMessage("project", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @Operation(summary = "update status of project")
    @PatchMapping("/{id}/status")
    public ResponseEntity<SimpleResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectStatusRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project status updated successfully");
        response.addMessage("project", projectService.updateProjectStatus(id, req));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @Operation(summary = "get project by project id")
    @GetMapping("/{id}")
    public  ResponseEntity<SimpleResponse> getById(@PathVariable Long id){
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project retrieved successfully");
        response.addMessage("project", projectService.getProjectById(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @Operation(summary = "update project Entity")
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SimpleResponse> patch(
            @PathVariable Long id,
            @RequestPart(value = "req", required = false) String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {
        PatchProjectRequest req = null;
        if (reqJson != null && !reqJson.isBlank()) {
            try {
                req = objectMapper.readValue(reqJson, PatchProjectRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON format in req parameter: " + e.getMessage());
            }
        }
        if (req == null && (file == null || file.isEmpty())) {
            throw new IllegalArgumentException("At least one of 'req' or 'file' must be provided");
        }
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project updated successfully");
        response.addMessage("project", projectService.updateProject(id, req, file));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @Operation(summary = "delete project by project id")
    @DeleteMapping("/{id}")
    public  ResponseEntity<SimpleResponse> delete(@PathVariable Long id){
        projectService.deleteProject(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message", "project deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @Operation(summary = "filter project and pages the result")
    @GetMapping("/filter/{msid}")
    public ResponseEntity<SimpleResponse> filterProject(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            @PathVariable Long msid
    ) {
        Pageable pageable = (Pageable) PageRequest.of(page, size);

        PageResponse<ProjectWithStatsResponse> response =
                projectService.getProject(projectName, status, msid, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "project retrieved successfully");
        simpleResponse.addMessage("projects", response);

        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/{projectId}/statistics")
    public ResponseEntity<SimpleResponse> getProjectStatistics(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        ProjectStatisticsDTO stats =
                projectService.getProjectStatistics(projectId, pageable);

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Project statistics retrieved successfully");
        resp.addMessage("projectStatistics", stats);

        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @GetMapping("/full-dashboard/{mentorshipId}")
    @Operation(summary = "get full project dashboard (dashboard + projects list)")
    public ResponseEntity<SimpleResponse> getFullDashboard(
            @PathVariable Long mentorshipId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        FullProjectDashBoardDto fullDashboard = projectService.getFullProjectDashboard(mentorshipId, projectName, status, pageable);
        
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Full dashboard retrieved successfully");
        resp.addMessage("fullDashboard", fullDashboard);
        
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "get project details with student submission in one response")
    @GetMapping("/{projectId}/student")
    public ResponseEntity<SimpleResponse> getProjectWithSubmission(
            @PathVariable Long projectId,
            Authentication authentication
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project with submission retrieved successfully");
        response.addMessage("data", submissionService.getProjectWithSubmission(projectId, authentication.getName()));
        return ResponseEntity.ok(response);
    }
}
