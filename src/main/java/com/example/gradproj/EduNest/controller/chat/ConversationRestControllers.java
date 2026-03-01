package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.chat.ConversationListResponse;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse;
import com.example.gradproj.EduNest.dto.chat.EditMessageRequest;
import com.example.gradproj.EduNest.service.chat.ConversationService;
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
public class ConversationRestControllers {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<SimpleResponse> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<ConversationMessageResponse> messages = conversationService.getConversationMessages(
                conversationId,
                beforeId,
                size
        );
        SimpleResponse response = new SimpleResponse();
        response.addMessage("messages", messages);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/messages/{messageId}")
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

        //ToDo review event in the receiver side

        messagingTemplate.convertAndSendToUser(
                authentication.getName(),
                "/queue/messages",
                messageResponse
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    public void delete(
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        conversationService.deleteMessage(
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
    }

    @GetMapping("/all")
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
