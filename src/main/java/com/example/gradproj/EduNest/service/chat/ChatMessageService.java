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
import com.example.gradproj.EduNest.service.chat.ChatAuthorization;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

    private final ChatMessageRepository messageRepo;
    private final ChatRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityService securityService;
    private final ChatAuthorization chatAuthorization;

    public ChatMessageResponse saveMessage(
            Long roomId,
            String content,
            ChatPrincipal principal
    ) {

        if (content == null || content.isBlank()) {
            throw new globalLogicEx("Message content must not be blank");
        }

        if (!roomRepo.existsById(roomId)) {

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Room not found with this id"
            );
            return null;
        }

        // P1-12 / EB-19 — defense in depth: the STOMP interceptor already
        // gates SEND frames, but nothing guarantees this method is only
        // reached through the channel chain. Re-check the D-14 WRITE rule
        // before persisting; unauthorized sends are dropped with no DB row.
        if (!chatAuthorization.canWriteRoom(roomId, principal.email())) {

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Forbidden: you are not a member of this room"
            );
            return null;
        }


        ChatRoom room = roomRepo.getReferenceById(roomId);

        UserEntity sender = userRepo.findByEmail(principal.email()).orElse(null);
        if (sender == null) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "User not found"
            );
            return null;
        }

        ChatMessage message = ChatMessage.builder()
                .content(content)
                .chatRoom(room)
                .senderEmail(principal.email())
                .senderName(principal.fullName())
                .sender(sender)
                .build();

        ChatMessage saved;
        try {
            saved = messageRepo.save(message);
        } catch (Exception ex) {
            log.error("Failed to save chat message in room {} for user {}", roomId, principal.email(), ex);
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Failed to save message. Please try again."
            );
            return null;
        }

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
                        pageable, isAdmin()
                );

        UUID roomPublicId = roomRepo.findById(roomId).map(com.example.gradproj.EduNest.entity.chat.ChatRoom::getPublicId).orElse(null);
        return messages.stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getMessageId())
                        .message(m.getContent())
                        .roomId(roomPublicId)
                        .time(m.getCreatedAt())
                        .senderName(m.getSenderName())
                        .senderEmail(m.getSenderEmail())
                        .senderProfileImageUrl(m.getSenderProfileImageUrl())
                        .build())
                .toList();
    }



    private ChatMessageResponse messageMapper(ChatMessage message, UUID roomId) {
        return ChatMessageResponse.builder()
                .id(message.getPublicId())
                .message(message.getContent())
                .roomId(roomId)
                .time(message.getCreatedAt())
                .senderName(message.getSenderName())
                .senderEmail(message.getSenderEmail())
                .senderProfileImageUrl(message.getSender() != null ? message.getSender().getProfileImageUrl() : null)
                .build();
    }

    private ChatMessageResponse messageMapper(ChatMessage message, Long roomId) {
        UUID roomPublicId = message.getChatRoom() != null ? message.getChatRoom().getPublicId() : null;
        return messageMapper(message, roomPublicId);
    }

    @Transactional
    public ChatMessageResponse editMessage(
            Long messageId,
            String senderEmail,
            String newContent
    ) {
        return editMessage(null, messageId, senderEmail, newContent);
    }

    @Transactional
    public ChatMessageResponse editMessage(
            Long roomId,
            Long messageId,
            String senderEmail,
            String newContent
    ) {
        if (newContent == null || newContent.isBlank()) {
            throw new globalLogicEx("Message content must not be blank");
        }

        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        if (roomId != null && !msg.getChatRoom().getId().equals(roomId)) {
            throw new globalLogicEx("Message does not belong to this room");
        }

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
        deleteMessage(null, messageId, senderEmail);
    }

    @Transactional
    public Long deleteMessage(
            Long roomId,
            Long messageId,
            String senderEmail
    ) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        if (roomId != null && !msg.getChatRoom().getId().equals(roomId)) {
            throw new globalLogicEx("Message does not belong to this room");
        }

        if (!msg.getSenderEmail().equals(senderEmail)) {
            throw new globalLogicEx("Not allowed to delete this message");
        }

        Long actualRoomId = msg.getChatRoom().getId();
        messageRepo.delete(msg);
        return actualRoomId;
    }


    private Long resolveRoomInternalId(UUID publicId) {
        return roomRepo.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Room not found with this id"));
    }

    private Long resolveMessageInternalId(UUID publicId) {
        return messageRepo.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));
    }

    public List<ChatMessageResponse> getRoomMessages(
            UUID roomId,
            Long beforeId,
            int size,
            String senderEmail
    ) {
        if (!securityService.isUserMemberOfChatRoom(roomId, senderEmail)) {
            throw new AccessDeniedException("You are not a member of this room");
        }
        Long internalRoomId = resolveRoomInternalId(roomId);
        Pageable pageable = PageRequest.of(0, size);
        List<ChatMessageProjection> messages = messageRepo.findMessagesByRoom(internalRoomId, beforeId, pageable, isAdmin());
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

    private boolean isAdmin() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Transactional
    public ChatMessageResponse editMessage(
            UUID roomId,
            UUID messageId,
            String senderEmail,
            String newContent
    ) {
        Long internalRoomId = roomId != null ? resolveRoomInternalId(roomId) : null;
        Long internalMsgId = resolveMessageInternalId(messageId);
        ChatMessageResponse resp = editMessage(internalRoomId, internalMsgId, senderEmail, newContent);
        if (roomId != null) {
            resp.setRoomId(roomId);
        }
        return resp;
    }

    @Transactional
    public UUID deleteMessage(
            UUID roomId,
            UUID messageId,
            String senderEmail
    ) {
        Long internalRoomId = roomId != null ? resolveRoomInternalId(roomId) : null;
        Long internalMsgId = resolveMessageInternalId(messageId);
        deleteMessage(internalRoomId, internalMsgId, senderEmail);
        return roomId;
    }

}
