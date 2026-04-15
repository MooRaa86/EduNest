package com.example.gradproj.EduNest.controller.studentAchievementController;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.studentAchievement.StudentAchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/achievements")
@RequiredArgsConstructor
@Tag(name = "Student Achievements", description = "Endpoints for student badges and project submissions")
public class StudentAchievementController {

    private final StudentAchievementService studentAchievementService;

    @GetMapping
    @Operation(summary = "Get student achievements: paginated badges and graded project submissions")
    public ResponseEntity<SimpleResponse> getAchievements(
            @RequestParam(defaultValue = "0") int badgesPage,
            @RequestParam(defaultValue = "10") int badgesSize,
            @RequestParam(defaultValue = "0") int projectsPage,
            @RequestParam(defaultValue = "10") int projectsSize
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Achievements retrieved successfully");
        response.addMessage("data", studentAchievementService.getAchievements(
                badgesPage, badgesSize,
                projectsPage, projectsSize
        ));
        return ResponseEntity.status(200).body(response);

    }
}