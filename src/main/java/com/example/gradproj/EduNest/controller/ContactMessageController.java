package com.example.gradproj.EduNest.controller;

import com.example.gradproj.EduNest.dto.ContactMessageRequestDto;
import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.ContactMessageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ContactMessageController {

    @Autowired
    private ContactMessageService contactMessageService;

    @PostMapping("/save-contact-message")
    public ResponseEntity<SimpleResponse> insertContactMessage(
            @Valid @RequestBody ContactMessageRequestDto contactMessageRequestDto) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message sent successfully");

        contactMessageService.saveContactMessage(contactMessageRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/all-contact-messages")
    public ResponseEntity<SimpleResponse> getAllContactMessages() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "All contact messages retrieved successfully");
        response.addMessage("Data", contactMessageService.getAllContactMessages());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
