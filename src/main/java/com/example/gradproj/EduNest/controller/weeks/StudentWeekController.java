package com.example.gradproj.EduNest.controller.weeks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.week.StudentWeekService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/week")
@RequiredArgsConstructor
@Tag(name = "Student Week", description = "APIs for students to view weeks and their contents")
public class StudentWeekController {

    private final StudentWeekService studentWeekService;

    @GetMapping("/{mentorshipId}/weeks")
    @Operation(summary = "Get all weeks for an enrolled mentorship")
    public ResponseEntity<SimpleResponse> getWeeks(@PathVariable Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "weeks retrieved successfully");
        response.addMessage("weeks", studentWeekService.getWeeksByMentorship(mentorshipId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{weekId}/contents")
    @Operation(summary = "Get published contents of a single week")
    public ResponseEntity<SimpleResponse> getWeekContents(@PathVariable Long weekId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "week contents retrieved successfully");
        response.addMessage("week", studentWeekService.getWeekContents(weekId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{mentorshipId}/weeks-with-contents")
    @Operation(summary = "Get all weeks with their published contents for an enrolled mentorship")
    public ResponseEntity<SimpleResponse> getWeeksWithContents(@PathVariable Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "weeks with contents retrieved successfully");
        response.addMessage("weeks", studentWeekService.getMentorshipWeeksWithContents(mentorshipId));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
