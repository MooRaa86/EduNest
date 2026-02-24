package com.example.gradproj.EduNest.controller.profile;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.profile.request.UpdateMentorProfileRequest;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileInformationResponse;
import com.example.gradproj.EduNest.service.profile.MentorProfileInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/mentor/profile")
@RequiredArgsConstructor
@Tag(name = "Mentor Profile", description = "Endpoints to manage mentor profile information")
public class MentorProfileInfoController {
    private final MentorProfileInfoService mentorProfileInfoService;

    @GetMapping
    @Operation(
            summary = "Get current mentor profile",
            description = "Returns the profile information of the currently authenticated mentor")
    public ResponseEntity<SimpleResponse> getMentorProfileInfo() {
        SimpleResponse simpleResponse = new SimpleResponse();
        MentorProfileInformationResponse  mentorProfileInformationResponse = mentorProfileInfoService.getCurrentUserInformation();
        simpleResponse.addMessage("Mentor-Profile-Information",mentorProfileInformationResponse);
        return ResponseEntity.ok(simpleResponse);
    }

    @PatchMapping
    @Operation(
            summary = "Update mentor profile",
            description = "Updates profile fields")
    public ResponseEntity<SimpleResponse>updateProfileInfo(@RequestBody UpdateMentorProfileRequest request) {
        SimpleResponse simpleResponse = new SimpleResponse();
        mentorProfileInfoService.updateProfile(request);
        simpleResponse.addMessage("message","Mentor-Profile-Information updated successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @PatchMapping("/image")
    @Operation(
            summary = "Update mentor profile image",
            description = "Uploads a new profile image for the currently authenticated mentor"
    )
    public ResponseEntity<SimpleResponse>updateProfileImage(@RequestParam("image") MultipartFile image) {
        SimpleResponse simpleResponse = new SimpleResponse();
        String imgUrl= mentorProfileInfoService.updateProfileImage(image);
        simpleResponse.addMessage("message", "Mentor profile image updated successfully");
        simpleResponse.addMessage("profileImageUrl", imgUrl);
        return ResponseEntity.ok(simpleResponse);
    }
}
