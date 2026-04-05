package com.example.gradproj.EduNest.controller.studentAchievement;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.studentAchievement.StudentAchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/achievements")
@Tag(name = " student achievements", description = "APIs for student achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final StudentAchievementService studentAchievementService;

    @GetMapping("/overview")
    @Operation(summary = "Get all badges and project submissions for the current student")
    public ResponseEntity<SimpleResponse> getStudentOverview(
            @RequestParam(defaultValue = "0") int badgesPage,
            @RequestParam(defaultValue = "10") int badgesSize,
            @RequestParam(defaultValue = "0") int projectsPage,
            @RequestParam(defaultValue = "10") int projectsSize
    ) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Student overview retrieved successfully");
        response.addMessage("data", studentAchievementService.getAchievements(badgesPage, badgesSize, projectsPage, projectsSize));
        return ResponseEntity.ok(response);
    }
}
