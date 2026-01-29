package com.example.gradproj.EduNest.controller.auth;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ForgetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.ResetPasswordRequestDto;
import com.example.gradproj.EduNest.dto.auth.forgetPassword.VerifyForgetPasswordOtpDto;
import com.example.gradproj.EduNest.service.auth.LoginService;
import com.example.gradproj.EduNest.service.register.RegistrationService;
import com.example.gradproj.EduNest.utils.Constants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final LoginService loginService;
    private final RegistrationService registerService;
    @PostMapping("/login-api")
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
    public ResponseEntity<SimpleResponse> forgetPassword(
            @Valid @RequestBody ForgetPasswordRequestDto dto) {

        registerService.forgetPassword(dto.getEmail());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message","OTP has sent to your email");
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }

    @PostMapping("/forget-password/verify-otp")
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
