package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.PatchTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping("/{id}/publish")
    public  ResponseEntity<SimpleResponse> publish(@PathVariable Long id){
        TaskResponse published=taskService.publish(id);
        SimpleResponse  response = new SimpleResponse();
        response.addMessage("message", "task published successfully");
        response.addMessage("task", published);
        return  ResponseEntity.status(HttpStatus.OK).body(response) ;
    }
    @GetMapping("/published")
    public  ResponseEntity<SimpleResponse> published(){

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "all published tasks");
        response.addMessage("tasks",taskService.getPublishedTasks());
       return  ResponseEntity.status(HttpStatus.OK).body(response);
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
    public  ResponseEntity<Void> delete(@PathVariable Long id){
        taskService.delete(id);
        return  ResponseEntity.noContent().build();
    }

}
