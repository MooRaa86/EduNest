package com.example.gradproj.EduNest.controller.projects;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.projects.request.GradeProjectSubmissionRequest;
import com.example.gradproj.EduNest.dto.projects.request.SubmitProjectRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.GradeTaskSubmissionRequest;
import com.example.gradproj.EduNest.dto.tasks.requests.SubmitTaskRequest;
import com.example.gradproj.EduNest.service.projects.ProjectServiceImpl;
import com.example.gradproj.EduNest.service.projects.ProjectSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
public class ProjectSubmissionController {
    private final ProjectSubmissionService submissionService;


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

    @GetMapping("/{projectId}/submissions")
    public ResponseEntity<SimpleResponse> listByProject(@PathVariable Long projectId) {
        SimpleResponse response=new SimpleResponse();
        response.addMessage("message","all submissions for this project");
        response.addMessage("submissions",submissionService.listByProject(projectId));
        return  ResponseEntity.status(HttpStatus.OK).body(response);
    }
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
