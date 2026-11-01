package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ConversationWebSocketControllers {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.private")
    public void sendPrivate(
            ConversationMessageRequest request,
            Principal principal
    ) {

        String email = principal.getName();

        ConversationMessageResponse msg =
                conversationService.sendMessage(email, request);

        // send to receiver (server-derived from persisted message / response)
        String recipient = msg.getRecipientEmail() != null ? msg.getRecipientEmail() : request.getRecipientEmail();
        messagingTemplate.convertAndSendToUser(
                recipient,
                "/queue/messages",
                msg
        );

        // send back to sender (update UI instantly)
        messagingTemplate.convertAndSendToUser(
                email,
                "/queue/messages",
                msg
        );
    }
}
