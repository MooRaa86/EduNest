package com.example.gradproj.EduNest.controller.profile;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.profile.request.UpdateStudentProfileRequest;
import com.example.gradproj.EduNest.service.profile.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/student/profile")
@RequiredArgsConstructor
@Tag(name = "Student Profile", description = "Endpoints to manage student profile information")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {
    private final StudentProfileService studentProfileService;

    @GetMapping
    @Operation(summary = "Get current student profile")
    public ResponseEntity<SimpleResponse> getStudentProfile() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("profile", studentProfileService.getStudentProfile());
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Operation(summary = "Update student profile")
    public ResponseEntity<SimpleResponse> updateStudentProfile(@RequestBody UpdateStudentProfileRequest request) {
        studentProfileService.updateStudentProfile(request);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Profile updated successfully");
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update student profile image")
    public ResponseEntity<SimpleResponse> updateProfileImage(@RequestParam("image") MultipartFile image) {
        SimpleResponse response = new SimpleResponse();
        String imgUrl = studentProfileService.updateProfileImage(image);
        response.addMessage("message", "Profile image updated successfully");
        response.addMessage("profileImageUrl", imgUrl);
        return ResponseEntity.ok(response);
    }
}
