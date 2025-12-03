package com.example.gradproj.EduNest.controller;

import com.example.gradproj.EduNest.dto.RegisterRequestDto;
import com.example.gradproj.EduNest.service.RegisterationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserManagmentController {

    private final RegisterationService registerationService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDto registerRequestDto){
        registerationService.registerUser(registerRequestDto);
        return ResponseEntity.ok("Registration Success for email " + registerRequestDto.getEmail());
    }

}
