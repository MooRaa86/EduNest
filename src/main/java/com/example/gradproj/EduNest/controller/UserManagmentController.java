package com.example.gradproj.EduNest.controller;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.StudenRequestDto;
import com.example.gradproj.EduNest.service.RegisterationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserManagmentController {

    private final RegisterationService registerationService;

    @PostMapping("/registerStudent")
    public ResponseEntity<SimpleResponse> registerStudent(@Valid @RequestBody StudenRequestDto dto){
        boolean state = registerationService.registerStudent(dto);
        SimpleResponse response = new SimpleResponse();
        if(state){
            response.addMessage("Registeration", "Student successfully registered! for email: " + dto.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }else {
            response.addMessage("Registeration", "Registration failed!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/registerMentor")
    public ResponseEntity<SimpleResponse> register(@Valid @RequestBody MentorRequestDto dto){
        boolean state = registerationService.registerMentor(dto);
        SimpleResponse response = new SimpleResponse();
        if(state) {
            response.addMessage("Registeration", "Mentor successfully registered! for email: " + dto.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }else {
            response.addMessage("Registeration", "Registration failed!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

}