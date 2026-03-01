package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.dto.chat.ConversationListResponse;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse;
import com.example.gradproj.EduNest.dto.chat.EditMessageRequest;
import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.repository.chat.projection.ConversationMessageProjection;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;

    public ConversationMessageResponse mapToResponse(Message msg) {
        return ConversationMessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderEmail(msg.getSender().getEmail())
                .senderName(msg.getSender().getFirstName() + " " + msg.getSender().getLastName())
                .sentAt(msg.getSentAt())
                .content(msg.getContent())
                .build();
    }



    public ConversationMessageResponse sendMessage(
            String senderEmail,
            ConversationMessageRequest request
    ) {

        UserEntity sender =
                userRepo.findByEmail(senderEmail).orElseThrow(
                        () -> new UsernameNotFoundException("Sender not found")
                );

        UserEntity receiver =
                userRepo.findByEmail(request.getRecipientEmail()).orElseThrow(
                        () -> new UsernameNotFoundException("Receiver not found")
                );

        // get or create conversation
        Conversation conversation =
                conversationRepo
                        .findBetweenUsers(senderEmail, request.getRecipientEmail())
                        .orElseGet(() -> createConversation(sender, receiver));

        Message msg = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .sentAt(LocalDateTime.now())
                .build();

        Message savedMessage =  messageRepo.save(msg);

        return mapToResponse(savedMessage);
    }

    public List<ConversationMessageResponse> getConversationMessages(
            Long conversationId,
            Long beforeId,
            int size
    ) {

        Pageable pageable = PageRequest.of(0, size);

        List<ConversationMessageProjection> messages =
                messageRepo.findConversationMessages(
                        conversationId,
                        beforeId,
                        pageable
                );

        return messages.stream()
                .map(m -> ConversationMessageResponse.builder()
                        .id(m.getMessageId())
                        .conversationId(m.getConversationId())
                        .content(m.getContent())
                        .sentAt(m.getSentAt())
                        .senderEmail(m.getSenderEmail())
                        .senderName(m.getSenderName())
                        .build())
                .toList();
    }

    @Transactional
    public ConversationMessageResponse editMessage(
            Long messageId,
            String senderEmail,
            EditMessageRequest request
    ) {

        Message msg = messageRepo.findById(messageId)
                .orElseThrow();

        // security check
//        if (!msg.getSenderEmail().equals(senderEmail)) {
//            throw new globalLogicEx("Not allowed");
//        }

        msg.setContent(request.getContent());
        msg.setUpdated(true);

        return mapToResponse(msg);
    }

    @Transactional
    public void deleteMessage(
            Long messageId,
            String senderEmail
    ) {
        int deleted =
                messageRepo.deleteByIdAndSender(
                        messageId,
                        senderEmail
                );

        if (deleted == 0) {
            throw new BadCredentialsException("Not allowed");
        }
    }

    public List<ConversationListResponse> getUserConversations(
            String email
    ) {

        return conversationRepo.findUserConversations(email)
                .stream()
                .map(c -> ConversationListResponse.builder()
                        .conversationId(c.getConversationId())
                        .otherUserEmail(c.getOtherUserEmail())
                        .otherUserName(c.getOtherUserName())
                        .lastMessage(c.getLastMessage())
                        .lastMessageTime(c.getLastMessageTime())
                        .build())
                .toList();
    }

    private Conversation createConversation(
            UserEntity u1,
            UserEntity u2) {

        Conversation c = Conversation.builder()
                .user1(u1)
                .user2(u2)
                .createdAt(LocalDateTime.now())
                .build();

        return conversationRepo.save(c);
    }
}
