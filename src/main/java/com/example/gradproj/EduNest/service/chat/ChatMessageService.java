package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.projection.ChatMessageProjection;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

    private final ChatMessageRepository messageRepo;
    private final ChatRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityService securityService;

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

        UserEntity sender = userRepo.findByEmail(principal.email()).orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        );

        ChatMessage message = ChatMessage.builder()
                .content(content)
                .chatRoom(room)
                .senderEmail(principal.email())
                .senderName(principal.fullName())
                .sender(sender)
                .build();

        ChatMessage saved = messageRepo.save(message);

        return messageMapper(saved, roomId);
    }


    public List<ChatMessageResponse> getRoomMessages(
            Long roomId,
            Long beforeId,
            int size,
            String senderEmail
    ) {

        if (!securityService.isUserMemberOfChatRoom(roomId, senderEmail)) {
            throw new AccessDeniedException("You are not a member of this room");
        }

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
                .id(message.getId())
                .message(message.getContent())
                .roomId(roomId)
                .time(message.getCreatedAt())
                .senderName(message.getSenderName())
                .senderEmail(message.getSenderEmail())
                .senderProfileImageUrl(message.getSender().getProfileImageUrl())
                .build();
    }

    @Transactional
    public ChatMessageResponse editMessage(
            Long messageId,
            String senderEmail,
            String newContent
    ) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new UsernameNotFoundException("Message not found"));

        if (!msg.getSenderEmail().equals(senderEmail)) {
            throw new globalLogicEx("Not allowed to edit this message");
        }

        msg.setContent(newContent);
        messageRepo.save(msg);

        return messageMapper(msg, msg.getChatRoom().getId());
    }

    @Transactional
    public void deleteMessage(
            Long messageId,
            String senderEmail
    ) {
        int deleted = messageRepo.deleteByIdAndSender(messageId, senderEmail);

        if (deleted == 0) {
            throw new BadCredentialsException("Not allowed to delete this message");
        }
    }

}
