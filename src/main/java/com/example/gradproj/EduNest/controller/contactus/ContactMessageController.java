package com.example.gradproj.EduNest.controller.contactus;

import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.service.contactus.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(
        name = "Contact Us",
        description = "APIs for sending and managing contact us messages"
)
public class ContactMessageController {


    private final ContactMessageService contactMessageService;

    @Operation(
            summary = "Send contact message",
            description = "Send a contact us message from users "
    )
    @PostMapping("/save-contact-message")
    public ResponseEntity<SimpleResponse> insertContactMessage(
            @Valid @RequestBody ContactMessageRequestDto contactMessageRequestDto) {

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message sent successfully");

        contactMessageService.saveContactMessage(contactMessageRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get all contact messages",
            description = "Retrieve all contact us messages"
    )
    @GetMapping("/all-contact-messages")
    public ResponseEntity<SimpleResponse> getAllContactMessages() {

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "All contact messages retrieved successfully");
        response.addMessage("Data", contactMessageService.getAllContactMessages());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
