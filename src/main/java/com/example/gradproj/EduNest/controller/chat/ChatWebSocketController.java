package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Room chat entry point. Binding contract (P0-03 / CHAT-08, EB-1 2026-10-29):
     * clients send a JSON object {@code {"content": "..."}} — bound via
     * {@code @Payload} into {@link ChatMessageRequest}. Raw string bodies are
     * rejected by the payload converter; a blank/missing content never
     * persists (guarded in ChatMessageService). API stays open so a reply /
     * attachment field can join the DTO without re-breaking the contract.
     */
    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload ChatMessageRequest request,
            ChatPrincipal principal
    ) {

        ChatMessageResponse response =
                messageService.saveMessage(
                        roomId,
                        request.getContent(),
                        principal
                );

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                response
        );
    }

}
