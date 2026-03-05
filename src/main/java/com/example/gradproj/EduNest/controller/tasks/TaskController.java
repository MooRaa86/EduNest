package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.FullTaskDashBoardDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskDashboardDTO;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.dto.tasks.response.TaskStatisticsDTO;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/v1/task")
@Tag(
        name = "task",
        description = "APIs for managing Tasks (create, update, delete, filter, dashboard"
)
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create task with optional file attachment")
    public ResponseEntity<SimpleResponse> create(
            @RequestPart("req") String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {
        CreateTaskRequest req = objectMapper.readValue(reqJson, CreateTaskRequest.class);
        TaskResponse created = taskService.createTask(req, file);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task created successfully");
        response.addMessage("task", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @Operation(summary = "update task ")
@PatchMapping("/{id}/status")
public ResponseEntity<SimpleResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateTaskStatusRequest req
) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task status updated successfully");
        response.addMessage("task", taskService.updateTaskStatus(id, req));
   return ResponseEntity.status(HttpStatus.OK).body(response);
}
    @GetMapping("/{id}")
    @Operation(summary = "get task by id")
    public  ResponseEntity<SimpleResponse> getById(@PathVariable Long id){
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task retrieved successfully");
        response.addMessage("task", taskService.getTaskById(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "update task with optional file attachment")
    public ResponseEntity<SimpleResponse> patch(
            @PathVariable Long id,
            @RequestPart(value = "req", required = false) String reqJson,
            @RequestPart(value = "file", required = false) MultipartFile file
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
       response.addMessage("task", taskService.updateTask(id, req, file));
       return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete task by id")
    public  ResponseEntity<SimpleResponse> delete(@PathVariable Long id){
        taskService.deleteTask(id);
        SimpleResponse simpleResponse=new SimpleResponse();
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
        Pageable pageable = (Pageable) PageRequest.of(page, size);

        PageResponse<TaskResponse> response =
                taskService.getTasks(taskName, status, msid, pageable);

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "Tasks retrieved successfully");
        simpleResponse.addMessage("Tasks", response);

        return ResponseEntity.ok(simpleResponse);
    }


    @GetMapping("/dashboard/{mentorshipId}")
    @Operation(summary = "get task dashboard details")
    public ResponseEntity<SimpleResponse> getDashboard(@PathVariable  Long mentorshipId) {
       TaskDashboardDTO taskDashboardDTO =taskService.getTaskDashboard(mentorshipId);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message","Dashboard retrieved successfully");
        simpleResponse.addMessage("Dashboard Details",taskDashboardDTO);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/{taskId}/statistics")
    @Operation(summary = "get task statistics (students/submissions + submissions page)")
    public ResponseEntity<SimpleResponse> getTaskStatistics(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        TaskStatisticsDTO stats = taskService.getTaskStatistics(taskId, pageable);

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Task statistics retrieved successfully");
        resp.addMessage("taskStatistics", stats);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/full-dashboard/{mentorshipId}")
    @Operation(summary = "get full task dashboard (dashboard + tasks list)")
    public ResponseEntity<SimpleResponse> getFullDashboard(
            @PathVariable Long mentorshipId,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        FullTaskDashBoardDto fullDashboard = taskService.getFullTaskDashboard(mentorshipId, taskName, status, pageable);
        
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", "Full dashboard retrieved successfully");
        resp.addMessage("fullDashboard", fullDashboard);
        
        return ResponseEntity.ok(resp);
    }

}
