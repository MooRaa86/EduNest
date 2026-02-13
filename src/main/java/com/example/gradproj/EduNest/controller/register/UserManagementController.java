package com.example.gradproj.EduNest.controller.register;

import com.example.gradproj.EduNest.dto.register.MentorRequestDto;
import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.register.StudentRequestDto;
import com.example.gradproj.EduNest.dto.register.VerifyAccountDto;
import com.example.gradproj.EduNest.service.register.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
@Tag(
        name = "Registration",
        description = "process of registration (student,mentor) , otp verification"
)
public class UserManagementController {

    private final RegistrationService registerationService;

    @PostMapping("/student")
    @Operation(summary = "register student")
    public ResponseEntity<SimpleResponse> registerStudent(@Valid @RequestBody StudentRequestDto dto){
        registerationService.registerStudent(dto);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("registration", "Student registered successfully and otp sent to Email: " + dto.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/mentor")
    @Operation(summary = "register mentor")
    public ResponseEntity<SimpleResponse> register(@Valid @RequestBody MentorRequestDto dto){
        registerationService.registerMentor(dto);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("registration", "Mentor registered successfully and otp sent to Email: " + dto.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-user")
    @Operation(summary = "verify email with the otp")
    public ResponseEntity<SimpleResponse> verifyAccount(@RequestBody VerifyAccountDto dto) {
        registerationService.verifyUser(dto.getEmail(), dto.getOtp());
        SimpleResponse response = new SimpleResponse();
        response.addMessage("verification", "Account verified successfully. Email: " + dto.getEmail());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/send-otp")
    @Operation(summary = "send new otp to the registered email")
    public ResponseEntity<SimpleResponse> sendOtpToEmail(@Valid @Email String email) {
        registerationService.generateAndSendOtp(email);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "OTP sent successfully. Email: " + email);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}