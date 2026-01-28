package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.UpdateTaskStatusRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;


@RestController
@RequestMapping("/api/v1/task")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
    @PostMapping
    public ResponseEntity<SimpleResponse> create(@RequestBody CreateTaskRequest req){
        TaskResponse created =taskService.create(req);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task created successfully");
        response.addMessage("task", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

@PatchMapping("/{id}/status")
public ResponseEntity<SimpleResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateTaskStatusRequest req
) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task status updated successfully");
        response.addMessage("task", taskService.updateStatus(id, req));
   return ResponseEntity.status(HttpStatus.OK).body(response);
}
    @GetMapping("/{id}")
    public  ResponseEntity<SimpleResponse> getById(@PathVariable Long id){
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task retrieved successfully");
        response.addMessage("task", taskService.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse> patch(
            @PathVariable Long id,
            @RequestBody @Valid PatchTaskRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "task updated successfully");
       response.addMessage("task", taskService.update(id, req));
       return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}")
    public  ResponseEntity<SimpleResponse> delete(@PathVariable Long id){
        taskService.delete(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message", "task deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @GetMapping("/filter/{msid}")
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

}
