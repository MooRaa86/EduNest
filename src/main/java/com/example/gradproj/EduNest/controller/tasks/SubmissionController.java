package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.tasks.GradeSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.SubmissionResponse;
import com.example.gradproj.EduNest.dto.tasks.SubmitTaskRequest;
import com.example.gradproj.EduNest.service.tasks.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }
    @PostMapping("/tasks/{taskId}/submissions")
    public ResponseEntity<SubmissionResponse> submit(
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitTaskRequest req
    ) {
        return ResponseEntity.ok(submissionService.submit(taskId, req));
    }

    @GetMapping("/tasks/{taskId}/submissions")
    public ResponseEntity<List<SubmissionResponse>> listByTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(submissionService.listByTask(taskId));
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionResponse> grade(
            @PathVariable Long submissionId,
            @Valid @RequestBody GradeSubmissionRequest req
    ) {
        return ResponseEntity.ok(submissionService.grade(submissionId, req));
    }
}

