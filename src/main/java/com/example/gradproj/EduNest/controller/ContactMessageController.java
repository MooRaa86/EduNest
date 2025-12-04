package com.example.gradproj.EduNest.controller;

import com.example.gradproj.EduNest.dto.ContactMessageRequestDto;
import com.example.gradproj.EduNest.service.ContactMessageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContactMessageController {

    @Autowired
    private ContactMessageService contactMessageService;

    @PostMapping("/saveContactMessage")
    public ResponseEntity<String> insertContactMessage(
            @Valid @RequestBody ContactMessageRequestDto contactMessageRequestDto) {

        contactMessageService.saveContactMessage(contactMessageRequestDto);
        return ResponseEntity.ok("Message sent successfully");
    }

    @GetMapping("/allContactMessages")
    public ResponseEntity<List<ContactMessageRequestDto>> getAllContactMessages() {
        return ResponseEntity.ok().body(contactMessageService.getAllContactMessages());
    }
}
