package com.example.gradproj.EduNest.controller.contactus;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.service.contactus.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contact")
@Tag(
        name = "Contact Us",
        description = "APIs for users to send messages or inquiries to the support team and view submitted messages"
)
public class ContactMessageController {

    @Autowired
    private ContactMessageService contactMessageService;

    @PostMapping("/save-message")
    @Operation(summary = "save contact message")
    public ResponseEntity<SimpleResponse> insertContactMessage(
            @Valid @RequestBody ContactMessageRequestDto contactMessageRequestDto) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message sent successfully");

        contactMessageService.saveContactMessage(contactMessageRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/all-messages")
    @Operation(summary = "get all messages")
    public ResponseEntity<SimpleResponse> getAllContactMessages() {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "All contact messages retrieved successfully");
        response.addMessage("Data", contactMessageService.getAllContactMessages());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
