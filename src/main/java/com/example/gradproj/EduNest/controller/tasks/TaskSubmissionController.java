package com.example.gradproj.EduNest.controller.tasks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.service.tasks.TaskSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/task-submission")
@Tag(
        name = "Task Submissions",
        description = "APIS"
)
public class TaskSubmissionController {
    private final TaskSubmissionService submissionService;

    public TaskSubmissionController(TaskSubmissionService submissionService) {
        this.submissionService = submissionService;
    }
    @PostMapping(value = "/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "submit task answer with file upload or external URL")
    public ResponseEntity<SimpleResponse> submit(
            @PathVariable Long taskId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fileUrl", required = false) String fileUrl
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message","task submitted Successfully");
        response.addMessage("submission",submissionService.submit(taskId, file, fileUrl));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "get task submissions")
    public ResponseEntity<SimpleResponse> listByTask(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "all submissions for this task");
        response.addMessage("submissions",
                submissionService.listByTask(taskId, page, size));

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{submissionId}/grade")
    @Operation(summary = "grade task submissions by submission id")
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

