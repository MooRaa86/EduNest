package com.example.gradproj.EduNest.controller.projects;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.projects.request.CreateProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.PatchProjectRequest;
import com.example.gradproj.EduNest.dto.projects.request.UpdateProjectStatusRequest;
import com.example.gradproj.EduNest.dto.projects.response.ProjectResponse;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.service.projects.ProjectServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectServiceImpl projectService;

    @PostMapping
    public ResponseEntity<SimpleResponse> create(@RequestBody CreateProjectRequest req){
        ProjectResponse created =projectService.createProject(req);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project created successfully");
        response.addMessage("project", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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

    @GetMapping("/{id}")
    public  ResponseEntity<SimpleResponse> getById(@PathVariable Long id){
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project retrieved successfully");
        response.addMessage("project", projectService.getProjectById(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> patch(
            @PathVariable Long id,
            @RequestBody @Valid PatchProjectRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "project updated successfully");
        response.addMessage("project", projectService.updateProject(id, req));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}")
    public  ResponseEntity<SimpleResponse> delete(@PathVariable Long id){
        projectService.deleteProject(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message", "project deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/filter/{msid}")
    public ResponseEntity<SimpleResponse> filterTasks(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            @PathVariable Long msid
    ) {
        Pageable pageable = (Pageable) PageRequest.of(page, size);

        PageResponse<ProjectResponse> response =
                projectService.getProject(projectName, status, msid, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "project retrieved successfully");
        simpleResponse.addMessage("projects", response);

        return ResponseEntity.ok(simpleResponse);
    }
}
