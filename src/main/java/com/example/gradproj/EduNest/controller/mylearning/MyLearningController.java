package com.example.gradproj.EduNest.controller.mylearning;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.mylearning.MyLearningService;
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

@RestController
@RequestMapping("/api/v1/my-learning")
@Tag(name = "My Learning", description = "APIs for student learning overview")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class MyLearningController {

    private final MyLearningService myLearningService;

    @GetMapping
    @Operation(summary = "Get student learning overview: completed mentorships, average progress, total points, and paginated active mentorships")
    public ResponseEntity<SimpleResponse> getMyLearning(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("data", myLearningService.getMyLearning(authentication.getName(), page, size));
        return ResponseEntity.ok(response);
    }
}
