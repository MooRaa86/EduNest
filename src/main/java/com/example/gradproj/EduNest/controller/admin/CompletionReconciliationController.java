package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.completion.CompletionReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/completion-jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CompletionReconciliationController {
    private final CompletionReconciliationService reconciliation;
    public record RetryRequest(String reason) { }

    @PostMapping("/{jobId}/retry")
    public ResponseEntity<SimpleResponse> retry(@PathVariable long jobId, @RequestBody RetryRequest request) {
        reconciliation.retryExhausted(jobId, request.reason());
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Retry scheduled");
        return ResponseEntity.ok(response);
    }
}
