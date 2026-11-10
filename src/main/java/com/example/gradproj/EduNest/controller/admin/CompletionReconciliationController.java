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
import java.util.UUID;
import com.example.gradproj.EduNest.exception.ApiError;
import com.example.gradproj.EduNest.exception.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/admin/mentorships")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CompletionReconciliationController {
    private final CompletionReconciliationService reconciliation;
    public record RetryRequest(String reason) { }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidIdentifier(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ApiErrorCode.ERR_VALIDATION, "Please check the submitted fields.", request.getRequestURI()));
    }

    @PostMapping("/{mentorshipId}/completion-job/retry")
    public ResponseEntity<SimpleResponse> retry(@PathVariable UUID mentorshipId, @RequestBody RetryRequest request) {
        reconciliation.retryExhausted(mentorshipId, request.reason());
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Retry scheduled");
        return ResponseEntity.ok(response);
    }
}
