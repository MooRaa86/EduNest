package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.projection.ChatMessageProjection;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepo;
    private final ChatRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageResponse saveMessage(
            Long roomId,
            String content,
            ChatPrincipal principal
    ) {

        if (!roomRepo.existsById(roomId)) {

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Room not found with this id"
            );
            return null;
        }


        ChatRoom room = roomRepo.getReferenceById(roomId);

        ChatMessage message = ChatMessage.builder()
                .content(content)
                .chatRoom(room)
                .senderEmail(principal.email())
                .senderName(principal.fullName())
                .sender(userRepo.getReferenceById(userRepo.findIdByEmail(principal.email()).orElseThrow(
                        () -> new RuntimeException("User not found")
                )))
                .build();

        ChatMessage saved = messageRepo.save(message);

        return messageMapper(saved, roomId);
    }


    public List<ChatMessageResponse> getRoomMessages(
            Long roomId,
            Long beforeId,
            int size
    ) {

        Pageable pageable = PageRequest.of(0, size);

        List<ChatMessageProjection> messages =
                messageRepo.findMessagesByRoom(
                        roomId,
                        beforeId,
                        pageable
                );

        return messages.stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getMessageId())
                        .message(m.getContent())
                        .roomId(roomId)
                        .time(m.getCreatedAt())
                        .senderName(m.getSenderName())
                        .senderEmail(m.getSenderEmail())
                        .senderProfileImageUrl(m.getSenderProfileImageUrl())
                        .build())
                .toList();
    }



    private ChatMessageResponse messageMapper(ChatMessage message,Long roomId) {
        return ChatMessageResponse.builder()
                .message(message.getContent())
                .roomId(roomId)
                .time(message.getCreatedAt())
                .senderName(message.getSenderName())
                .senderEmail(message.getSenderEmail())
                .senderProfileImageUrl(message.getSender().getProfileImageUrl())
                .build();
    }

}
