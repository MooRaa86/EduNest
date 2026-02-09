package com.example.gradproj.EduNest.controller.auth;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ForgetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ResetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.VerifyForgetPasswordOtpDto;
import com.example.gradproj.EduNest.service.auth.LoginService;
import com.example.gradproj.EduNest.service.register.RegistrationService;
import com.example.gradproj.EduNest.utils.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Authentication (login, forget password)"
)
public class AuthController {
    private final LoginService loginService;
    private final RegistrationService registerService;

    @PostMapping("/login-api")
    @Operation(summary = "login with email and password")
    public ResponseEntity<SimpleResponse> login(
            @RequestBody LoginRequestDto DTO
            ){
        String jwt = loginService.loginProcess(DTO);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status","Login successful");
        resp.addMessage("jwt",jwt);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header(Constants.JWT_HEADER,jwt)
                .body(resp);
    }

    @PostMapping("/forget-password")
    @Operation(summary = "send otp via email for password reset")
    public ResponseEntity<SimpleResponse> forgetPassword(
            @Valid @RequestBody ForgetPasswordRequestDto dto) {

        registerService.forgetPassword(dto.getEmail());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message","OTP has sent to your email");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/forget-password/verify-otp")
    @Operation(summary = "verify the otp")
    public ResponseEntity<SimpleResponse> verifyForgetPasswordOtp(
            @Valid @RequestBody VerifyForgetPasswordOtpDto dto) {

        registerService.verifyForgetPasswordOtp(
                dto.getEmail(),
                dto.getOtp()
        );
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message","OTP verified successfully. You can reset your password within the next 1 hours.");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/forget-password/reset")
    @Operation(summary = "reset password after verification")
    public ResponseEntity<SimpleResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto dto) {

        registerService.resetPassword(
                dto.getEmail(),
                dto.getNewPassword()
        );
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message","password has been reset successfully");
        return ResponseEntity.status(HttpStatus.OK).body(resp);

    }

}
