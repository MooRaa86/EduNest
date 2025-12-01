package com.example.gradproj.EduNest.controller.auth;

import com.example.gradproj.EduNest.dto.auth.RegisterRequest;
import com.example.gradproj.EduNest.entity.User;
import com.example.gradproj.EduNest.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User created = authService.register(request);
        Map<String, Object> body = Map.of(
                "message", "User registered successfully",
                "userId", created.getId(),
                "email", created.getEmail()
        );
        return ResponseEntity.status(201).body(body);
}}
