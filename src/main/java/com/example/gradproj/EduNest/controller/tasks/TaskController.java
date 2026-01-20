package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.tasks.CreateTaskRequest;
import com.example.gradproj.EduNest.dto.tasks.TaskResponse;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1-tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
    @PostMapping
    public ResponseEntity<TaskResponse> create(@RequestBody CreateTaskRequest req){
        return  ResponseEntity.ok(taskService.create(req));
    }
    @PostMapping("/{id}/publish")
    public  ResponseEntity<TaskResponse> publish(@PathVariable Long id){
        return   ResponseEntity.ok(taskService.publish(id));
    }
    @GetMapping("/published")
    public  ResponseEntity<List<TaskResponse>> published(){
        return ResponseEntity.ok(taskService.getPublishedTasks());
    }
    @GetMapping("/{id}")
    public  ResponseEntity<TaskResponse> getById(@PathVariable Long id){
        return   ResponseEntity.ok(taskService.getById(id));
    }

}
