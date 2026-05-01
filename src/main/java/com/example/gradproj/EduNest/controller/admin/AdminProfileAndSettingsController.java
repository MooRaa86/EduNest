package com.example.gradproj.EduNest.controller.admin;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.dto.profile.request.UpdateAdminProfileRequest;
import com.example.gradproj.EduNest.service.admin.AdminProfileAndSettingsService;
import com.example.gradproj.EduNest.service.mentorShip.CommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Profile & Settings", description = "Endpoints to manage admin profile and platform settings")
public class AdminProfileAndSettingsController {

    private final AdminProfileAndSettingsService adminService;
    private final CommissionService commissionService;

    @GetMapping("/profile")
    @Operation(summary = "Get admin profile")
    public ResponseEntity<SimpleResponse> getProfile() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("profile", adminService.getAdminProfileInfo());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update admin profile")
    public ResponseEntity<SimpleResponse> updateProfile(@RequestBody UpdateAdminProfileRequest request) {
        adminService.updateAdminProfileInfo(request);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Profile updated successfully");
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update admin profile image")
    public ResponseEntity<SimpleResponse> updateProfileImage(@RequestParam("image") MultipartFile image) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("profileImageUrl", adminService.updateProfileImage(image));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/profile/change-email/request")
    @Operation(summary = "Request email change")
    public ResponseEntity<SimpleResponse> requestChangeEmail(@RequestParam String newEmail) {
        adminService.requestChangeEmail(newEmail);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "OTP sent to new email");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/profile/change-email/confirm")
    @Operation(summary = "Confirm email change with OTP")
    public ResponseEntity<SimpleResponse> confirmChangeEmail(@RequestParam String otpCode) {
        adminService.confirmChangeEmail(otpCode);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Email changed successfully");
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/profile/change-password")
    @Operation(summary = "Change admin password")
    public ResponseEntity<SimpleResponse> changePassword(@RequestBody ChangePasswordRequest request) {
        adminService.changePassword(request);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Password changed successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/commission")
    @Operation(summary = "Get current commission rate")
    public ResponseEntity<SimpleResponse> getCommissionRate() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("commissionRate", commissionService.getCommissionRate());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/commission")
    @Operation(summary = "Update commission rate")
    public ResponseEntity<SimpleResponse> updateCommissionRate(@RequestParam Double rate) {
        commissionService.setCommissionRate(rate);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Commission rate updated successfully");
        return ResponseEntity.ok(response);
    }
}
