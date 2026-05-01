package com.example.gradproj.EduNest.controller.contactus;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.dto.contactus.ContactMessageResponseDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.enums.message.MessageStatus;
import com.example.gradproj.EduNest.service.contactus.ContactMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contact")
@Tag(
        name = "Contact Us",
        description = "APIs for users to send messages or inquiries to the support team and view submitted messages"
)
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

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

    @GetMapping("/message/{id}")
    @Operation(summary = "get specific message by ID")
    public ResponseEntity<SimpleResponse> getSpecificMessage(@PathVariable Long id) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message retrieved successfully");
        response.addMessage("Data", contactMessageService.getSpecificMessage(id));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/message/{id}/status")
    @Operation(summary = "update message status")
    public ResponseEntity<SimpleResponse> updateMessageStatus(
            @PathVariable Long id,
            @RequestParam MessageStatus status) {
        contactMessageService.updateMessageStatus(id, status);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message status updated successfully");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/messages/filter")
    @Operation(summary = "filter messages by status with pagination")
    public ResponseEntity<SimpleResponse> filterMessages(
            @RequestParam MessageStatus status,
            Pageable pageable) {
        PageResponse<ContactMessageResponseDto> result =
                contactMessageService.filterMessages(status, pageable);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Messages filtered successfully");
        response.addMessage("Data", result);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/message/{id}/reply")
    @Operation(summary = "send admin reply via email")
    public ResponseEntity<SimpleResponse> sendAdminReply(
            @PathVariable Long id,
            @RequestBody String reply) {
        contactMessageService.sendAdminReply(id, reply);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Reply sent successfully");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/message/{id}/notification")
    @Operation(summary = "send notification to message sender")
    public ResponseEntity<SimpleResponse> sendNotificationToMessageSender(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestBody String content) {
        contactMessageService.sendNotificationToMessageSender(id, title, content);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Notification sent successfully");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/message/{id}")
    @Operation(summary = "delete specific message")
    public ResponseEntity<SimpleResponse> deleteMessage(@PathVariable Long id) {
        contactMessageService.deleteMessage(id);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "Message deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/messages")
    @Operation(summary = "delete all messages")
    public ResponseEntity<SimpleResponse> deleteAllMessages() {
        contactMessageService.deleteAllMessages();
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status", "All messages deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
