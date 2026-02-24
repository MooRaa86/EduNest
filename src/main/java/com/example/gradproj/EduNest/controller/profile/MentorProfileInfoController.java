package com.example.gradproj.EduNest.controller.profile;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.profile.request.UpdateMentorProfileRequest;
import com.example.gradproj.EduNest.dto.profile.response.MentorProfileInformationResponse;
import com.example.gradproj.EduNest.service.profile.MentorProfileInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/mentor/profile")
@RequiredArgsConstructor
public class MentorProfileInfoController {
    private final MentorProfileInfoService mentorProfileInfoService;

    @GetMapping
    public ResponseEntity<SimpleResponse> getMentorProfileInfo() {
        SimpleResponse simpleResponse = new SimpleResponse();
        MentorProfileInformationResponse  mentorProfileInformationResponse = mentorProfileInfoService.getCurrentUserInformation();
        simpleResponse.addMessage("Mentor-Profile-Information",mentorProfileInformationResponse);
        return ResponseEntity.ok(simpleResponse);
    }

    @PatchMapping
    public ResponseEntity<SimpleResponse>updateProfileInfo(@RequestBody UpdateMentorProfileRequest request) {
        SimpleResponse simpleResponse = new SimpleResponse();
        mentorProfileInfoService.updateProfile(request);
        simpleResponse.addMessage("message","Mentor-Profile-Information updated successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @PatchMapping("/image")
    public ResponseEntity<SimpleResponse>updateProfileImage(@RequestParam("image") MultipartFile image) {
        SimpleResponse simpleResponse = new SimpleResponse();
        String imgUrl= mentorProfileInfoService.updateProfileImage(image);
        simpleResponse.addMessage("message", "Mentor profile image updated successfully");
        simpleResponse.addMessage("profileImageUrl", imgUrl);
        return ResponseEntity.ok(simpleResponse);
    }
}
