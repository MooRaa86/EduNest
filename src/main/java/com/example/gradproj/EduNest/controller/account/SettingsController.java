package com.example.gradproj.EduNest.controller.account;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.account.request.ChangeEmailRequest;
import com.example.gradproj.EduNest.dto.account.request.ChangePasswordRequest;
import com.example.gradproj.EduNest.dto.account.request.UpdateSettingsRequest;
import com.example.gradproj.EduNest.dto.account.response.SettingsResponse;
import com.example.gradproj.EduNest.service.account.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings")
@Tag(
        name = "Account Settings",
        description = "APIs for managing user account settings"
)
public class SettingsController {

    private final SettingsService settingsService;



    @Operation(
            summary = "Change user email",
            description = "Change the user's email address. Requires password validation. Forces logout after change."
    )
    @PatchMapping("/email")
    public ResponseEntity<SimpleResponse> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        settingsService.changeEmail(request);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("Settings Updated", "Email Changed Successfully");
        simpleResponse.addMessage("forceLogout", "Please login again");
        return ResponseEntity.ok(simpleResponse);
    }

    @Operation(
            summary = "Change user password",
            description = "Change the user's password. Requires old password and confirmation of new password."
    )
    @PatchMapping("/password")
    public ResponseEntity<SimpleResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        settingsService.changePassword(request);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("Settings Updated", "Password Changed Successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @Operation(
            summary = "Request account deletion",
            description = "Send OTP to user's email to confirm account deletion"
    )
    @PostMapping("/delete")
    public ResponseEntity<SimpleResponse> deleteAccount() {
        settingsService.deleteAccount();
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("Account Delete Request", "Otp Sent Successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @Operation(
            summary = "Confirm account deletion",
            description = "Confirm account deletion by providing the OTP received via email"
    )
    @PostMapping("/confirm-delete")
    public ResponseEntity<SimpleResponse> confirmDeleteAccount(@RequestParam String otp) {
        settingsService.confirmDeleteAccount(otp);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("Account Deleted", "Account Deleted Successfully");
        return ResponseEntity.ok(simpleResponse);
    }

    @Operation(
            summary = "Deactivate account",
            description = "Deactivate account  by providing the Password"
    )
    @PostMapping("/deactivate")
    public ResponseEntity<SimpleResponse>deactivateAccount(@RequestParam String password) {
        SimpleResponse simpleResponse = new SimpleResponse();
        settingsService.deactivateAccount(password);
        simpleResponse.addMessage("Account Deactivated", "Account Deactivated Successfully");
        return ResponseEntity.ok(simpleResponse);
    }

}