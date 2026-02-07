package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.service.tasks.TaskSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaskSubmissionController {
    private final TaskSubmissionService submissionService;

    public TaskSubmissionController(TaskSubmissionService submissionService) {
        this.submissionService = submissionService;
    }
    @PostMapping("/tasks/{taskId}/submissions")
    public ResponseEntity<SimpleResponse> submit(
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitTaskRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message","task submitted Successfully");
        response.addMessage("submission",submissionService.submit(taskId,req));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/tasks/{taskId}/submissions")
    public ResponseEntity<SimpleResponse> listByTask(@PathVariable Long taskId) {
       SimpleResponse response=new SimpleResponse();
       response.addMessage("message","all submissions for this task");
       response.addMessage("submissions",submissionService.listByTask(taskId));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SimpleResponse> grade(
            @PathVariable Long submissionId,
            @Valid @RequestBody GradeTaskSubmissionRequest req
    ) {
        SimpleResponse response=new SimpleResponse();
        response.addMessage("message","grade submitted Successfully");
        response.addMessage("submission",submissionService.grade(submissionId,req));
       return  ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

