package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomRepository chatRoomRepository;

    /**
     * Room chat entry point. Binding contract (P0-03 / CHAT-08, EB-1 2026-10-29):
     * clients send a JSON object {@code {"content": "..."}} — bound via
     * {@code @Payload} into {@link ChatMessageRequest}.
     * Accepts either UUID or numeric roomId as destination variable.
     */
    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable String roomId,
            @Payload ChatMessageRequest request,
            ChatPrincipal principal
    ) {
        Long internalRoomId;
        try {
            internalRoomId = Long.parseLong(roomId);
        } catch (NumberFormatException e) {
            try {
                UUID publicId = UUID.fromString(roomId);
                internalRoomId = chatRoomRepository.findIdByPublicId(publicId).orElse(null);
            } catch (IllegalArgumentException ex) {
                internalRoomId = null;
            }
        }

        if (internalRoomId == null) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Room not found with this id"
            );
            return;
        }

        ChatMessageResponse response =
                messageService.saveMessage(
                        internalRoomId,
                        request.getContent(),
                        principal
                );

        if (response != null) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    response
            );
            if (response.getRoomId() != null && !response.getRoomId().toString().equals(roomId)) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + response.getRoomId(),
                        response
                );
            }
            if (!internalRoomId.toString().equals(roomId)) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + internalRoomId,
                        response
                );
            }
        }
    }

    public void sendMessage(Long roomId, ChatMessageRequest request, ChatPrincipal principal) {
        sendMessage(String.valueOf(roomId), request, principal);
    }

}
