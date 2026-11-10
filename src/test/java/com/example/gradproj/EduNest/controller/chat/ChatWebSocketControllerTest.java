package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EB-55 / CHAT-01: ChatWebSocketController Reliability & Null-Broadcast Suppression")
class ChatWebSocketControllerTest {

    @Mock
    private ChatMessageService messageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    @Test
    @DisplayName("CHAT-01: When message persistence succeeds, broadcast response to /topic/room/{roomId}")
    void sendMessage_whenSaveSuccessful_broadcastsToTopic() {
        Long roomId = 42L;
        ChatMessageRequest request = ChatMessageRequest.builder().content("Hello world").build();
        ChatPrincipal principal = new ChatPrincipal("alice@example.com", "Alice Smith");

        UUID messageId = UUID.randomUUID();
        UUID roomPublicId = UUID.randomUUID();

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(messageId)
                .message("Hello world")
                .roomId(roomPublicId)
                .senderName("Alice Smith")
                .senderEmail("alice@example.com")
                .time(LocalDateTime.now())
                .build();

        when(messageService.saveMessage(roomId, "Hello world", principal)).thenReturn(response);

        chatWebSocketController.sendMessage(roomId, request, principal);

        verify(messagingTemplate).convertAndSend(eq("/topic/room/42"), eq(response));
    }

    @Test
    @DisplayName("CHAT-01: When saveMessage returns null (persistence failure or auth drop), suppress topic broadcast")
    void sendMessage_whenSaveReturnsNull_suppressesBroadcast() {
        Long roomId = 42L;
        ChatMessageRequest request = ChatMessageRequest.builder().content("Hello world").build();
        ChatPrincipal principal = new ChatPrincipal("alice@example.com", "Alice Smith");

        when(messageService.saveMessage(roomId, "Hello world", principal)).thenReturn(null);

        chatWebSocketController.sendMessage(roomId, request, principal);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
