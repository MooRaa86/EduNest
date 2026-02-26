package com.example.gradproj.EduNest.controller.projects;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.request.SubmitProjectRequest;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/project")
@Tag(
        name = "project submission",
        description = "APIS"
)
@RequiredArgsConstructor
public class ProjectSubmissionController {
    private final ProjectSubmissionService submissionService;

    @Operation(summary = "submit by project Id")
    @PostMapping("/{projectId}/submissions")
    public ResponseEntity<SimpleResponse> submit(
            @PathVariable Long projectId,
            @Valid @RequestBody SubmitProjectRequest req
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message","project submitted Successfully");
        response.addMessage("submission",submissionService.submit(projectId,req));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "get submissions by project Id")
    @GetMapping("/{projectId}/submissions")
    public ResponseEntity<SimpleResponse> listByProject(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "all submissions for this project");
        response.addMessage("submissions", submissionService.listByProject(projectId, page, size));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "grade project submission by submission Id")
    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SimpleResponse> grade(
            @PathVariable Long submissionId,
            @Valid @RequestBody GradeProjectSubmissionRequest req
    ) {
        SimpleResponse response=new SimpleResponse();
        response.addMessage("message","grade submitted Successfully");
        response.addMessage("submission",submissionService.gradeProject(submissionId,req));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
