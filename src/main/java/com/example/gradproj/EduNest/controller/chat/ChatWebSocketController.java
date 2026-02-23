package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            String content,
            ChatPrincipal principal
    ) {

        ChatMessageResponse response =
                messageService.saveMessage(
                        roomId,
                        content,
                        principal
                );

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                response
        );
    }

}
