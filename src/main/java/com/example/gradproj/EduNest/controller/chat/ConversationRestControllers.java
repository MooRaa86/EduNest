package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.chat.ConversationListResponse;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse;
import com.example.gradproj.EduNest.dto.chat.DeleteMessageRequest;
import com.example.gradproj.EduNest.dto.chat.EditMessageRequest;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversation")
@RequiredArgsConstructor
@Tag(
        name = "Direct Chat",
        description = "Conversation rest apis"
)
public class ConversationRestControllers {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "Get messages of a conversation")
    public ResponseEntity<SimpleResponse> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<ConversationMessageResponse> messages = conversationService.getConversationMessages(
                conversationId,
                beforeId,
                size,
                email,
                isAdmin
        );
        SimpleResponse response = new SimpleResponse();
        response.addMessage("messages", messages);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message")
    public ResponseEntity<SimpleResponse> edit(
            @PathVariable Long messageId,
            @RequestBody EditMessageRequest request,
            Authentication authentication
    ) {
        ConversationMessageResponse messageResponse = conversationService.editMessage(
                messageId,
                authentication.getName(),
                request
        );
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message after update", messageResponse);

        messagingTemplate.convertAndSendToUser(
                authentication.getName(),
                "/queue/messages",
                messageResponse
        );
        
        // CHAT-04: recipient email derived from conversation, not client DTO
        if (messageResponse.getRecipientEmail() != null) {
            messagingTemplate.convertAndSendToUser(
                    messageResponse.getRecipientEmail(),
                    "/queue/messages",
                    messageResponse
            );
        }
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete a message")
    public void delete(
            @PathVariable Long messageId,
            @RequestBody(required = false) DeleteMessageRequest request,
            Authentication authentication
    ) {
        String recipientEmail = conversationService.deleteMessage(
                messageId,
                authentication.getName()
        );
        
        Map<String,Object> event = Map.of(
                "type","DELETE",
                "messageId", messageId
        );

        messagingTemplate.convertAndSendToUser(
                authentication.getName(),
                "/queue/messages",
                event
        );
        
        // CHAT-04: recipient email derived from conversation, not client DTO
        if (recipientEmail != null) {
            messagingTemplate.convertAndSendToUser(
                    recipientEmail,
                    "/queue/messages",
                    event
            );
        }
    }

    @GetMapping("/all")
    @Operation(summary = "Get all conversations of the authenticated user")
    public ResponseEntity<SimpleResponse> getMyConversations(
            Authentication authentication
    ) {
        List<ConversationListResponse> conversations = conversationService
                .getUserConversations(authentication.getName());

        SimpleResponse response = new SimpleResponse();
        response.addMessage("conversations", conversations);
        return ResponseEntity.ok(response);
    }
}
