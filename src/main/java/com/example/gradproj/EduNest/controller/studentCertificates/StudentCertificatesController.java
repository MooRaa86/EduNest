package com.example.gradproj.EduNest.controller.studentCertificates;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.certificate.CertificateService;
import com.example.gradproj.EduNest.service.completion.CompletionStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/student/certificates")
@RequiredArgsConstructor
@Tag(name = "Student Certificates", description = "Endpoints for student certificates")
@PreAuthorize("hasRole('STUDENT')")
public class StudentCertificatesController {

    private final CertificateService certificateService;
    private final CompletionStatusService completionStatusService;

    @GetMapping("/mentorships/{mentorshipId}/status")
    public ResponseEntity<SimpleResponse> getCompletionStatus(@PathVariable long mentorshipId, Authentication authentication) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", completionStatusService.status(mentorshipId, authentication.getName()));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get paginated student certificates ordered by issue date")
    public ResponseEntity<SimpleResponse> getMyCertificates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Certificates retrieved successfully");
        response.addMessage("data", certificateService.getStudentCertificates(authentication.getName(),page, size));
        return ResponseEntity.ok(response);
    }
}
