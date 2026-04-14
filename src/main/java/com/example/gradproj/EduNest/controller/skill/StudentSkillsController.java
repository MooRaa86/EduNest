package com.example.gradproj.EduNest.controller.skill;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.skill.StudentSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student/skills")
@RequiredArgsConstructor
@Tag(name = "Student Skills", description = "Endpoints to manage student skills")
public class StudentSkillsController {

    private final StudentSkillService studentSkillService;

    @GetMapping
    @Operation(summary = "Get all student skills")
    public ResponseEntity<SimpleResponse> getAllSkills() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("skills", studentSkillService.getAllStudentSkills());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Add a skill")
    public ResponseEntity<SimpleResponse> addSkill(@RequestParam String skillName) {
        studentSkillService.addSkill(skillName);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Skill added successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @Operation(summary = "Delete a skill")
    public ResponseEntity<SimpleResponse> deleteSkill(@RequestParam String skillName) {
        studentSkillService.deleteSkill(skillName);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Skill deleted successfully");
        return ResponseEntity.ok(response);
    }
}
