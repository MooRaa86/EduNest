package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.service.tasks.TaskService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/task")
@Tag(name = "task", description = "APIs for managing Tasks (create, update, delete, filter, dashboard")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create task with optional file attachment")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> create(
            @RequestPart("req") String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication
    ) throws Exception {
        CreateTaskRequest req = objectMapper.readValue(reqJson, CreateTaskRequest.class);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task created successfully");
        response.addMessage("task", taskService.createTask(req, file, authentication.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "update task status")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest req,
            Authentication authentication
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task status updated successfully");
        response.addMessage("task", taskService.updateTaskStatus(id, req, authentication.getName()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "get task by id")
    public ResponseEntity<SimpleResponse> getById(@PathVariable Long id) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task retrieved successfully");
        response.addMessage("task", taskService.getTaskById(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "update task with optional file attachment")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> patch(
            @PathVariable Long id,
            @RequestPart(value = "req", required = false) String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication
    ) throws Exception {
        PatchTaskRequest req = null;
        if (reqJson != null && !reqJson.isBlank()) {
            try {
                req = objectMapper.readValue(reqJson, PatchTaskRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON format in req parameter: " + e.getMessage());
            }
        }
        if (req == null && (file == null || file.isEmpty())) {
            throw new IllegalArgumentException("At least one of 'req' or 'file' must be provided");
        }
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task updated successfully");
        response.addMessage("task", taskService.updateTask(id, req, file, authentication.getName()));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete task by id")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> delete(@PathVariable Long id, Authentication authentication) {
        taskService.deleteTask(id, authentication.getName());
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "task deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/filter/{msid}")
    @Operation(summary = "filter tasks")
    public ResponseEntity<SimpleResponse> filterTasks(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            @PathVariable Long msid
    ) {
        Pageable pageable = PageRequest.of(page, size);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Tasks retrieved successfully");
        simpleResponse.addMessage("Tasks", taskService.getTasks(taskName, status, msid, pageable));
        return ResponseEntity.ok(simpleResponse);
    }

    @GetMapping("/dashboard/{mentorshipId}")
    @Operation(summary = "get task dashboard details")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> getDashboard(@PathVariable Long mentorshipId, Authentication authentication) {
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Dashboard retrieved successfully");
        simpleResponse.addMessage("Dashboard Details", taskService.getTaskDashboard(mentorshipId, authentication.getName()));
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/{taskId}/statistics")
    @Operation(summary = "get task statistics (students/submissions + submissions page)")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> getTaskStatistics(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Task statistics retrieved successfully");
        resp.addMessage("taskStatistics", taskService.getTaskStatistics(taskId, pageable, authentication.getName()));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/full-dashboard/{mentorshipId}")
    @Operation(summary = "get full task dashboard (dashboard + tasks list)")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> getFullDashboard(
            @PathVariable Long mentorshipId,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size,
            Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Full dashboard retrieved successfully");
        resp.addMessage("fullDashboard", taskService.getFullTaskDashboard(mentorshipId, taskName, status, pageable, authentication.getName()));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{taskId}/student")
    @Operation(summary = "get task details with student submission in one response")
    public ResponseEntity<SimpleResponse> getTaskWithSubmission(@PathVariable Long taskId, Authentication authentication) {
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Task with submission retrieved successfully");
        resp.addMessage("data", taskService.getTaskWithSubmission(taskId, authentication.getName()));
        return ResponseEntity.ok(resp);
    }
}
