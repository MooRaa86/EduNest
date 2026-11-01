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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    private final com.example.gradproj.EduNest.service.chat.ChatAuthorization chatAuthorization;

    public ConversationMessageResponse mapToResponse(Message msg) {
        Conversation c = msg.getConversation();
        String recipientEmail = null;
        if (c != null && msg.getSender() != null) {
            String senderEmail = msg.getSender().getEmail();
            if (c.getUser1() != null && senderEmail.equals(c.getUser1().getEmail())) {
                recipientEmail = c.getUser2() != null ? c.getUser2().getEmail() : null;
            } else if (c.getUser2() != null && senderEmail.equals(c.getUser2().getEmail())) {
                recipientEmail = c.getUser1() != null ? c.getUser1().getEmail() : null;
            }
        }

        return ConversationMessageResponse.builder()
                .id(msg.getId())
                .conversationId(c != null ? c.getId() : null)
                .senderEmail(msg.getSender().getEmail())
                .senderName(msg.getSender().getFirstName() + " " + msg.getSender().getLastName())
                .senderProfileImageUrl(msg.getSender().getProfileImageUrl())
                .recipientEmail(recipientEmail)
                .sentAt(msg.getSentAt())
                .content(msg.getContent())
                .build();
    }

    public ConversationMessageResponse sendMessage(
            String senderEmail,
            ConversationMessageRequest request
    ) {

        // P1-12 / EB-19 — sender↔recipient pair validation: the recipient must
        // be a distinct user (existence is enforced by the lookup below).
        // Client-controlled event ROUTING (CHAT-04) is re-derived server-side
        // under P1-13; the pair shape itself is guarded here.
        chatAuthorization.validatePrivatePair(senderEmail, request.getRecipientEmail());

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

    /**
     * CHAT-02 (P1-13 / EB-20): Only conversation participants or ADMIN can read messages.
     * Prevents IDOR and enumeration oracles.
     */
    public List<ConversationMessageResponse> getConversationMessages(
            Long conversationId,
            Long beforeId,
            int size,
            String requesterEmail,
            boolean isAdmin
    ) {
        Conversation conversation = conversationRepo.findById(conversationId).orElse(null);
        if (conversation == null) {
            if (isAdmin) {
                throw new EntityNotFoundException("Conversation not found");
            }
            throw new AccessDeniedException("Not authorized to view this conversation");
        }

        boolean isParticipant = (conversation.getUser1() != null && conversation.getUser1().getEmail().equals(requesterEmail))
                || (conversation.getUser2() != null && conversation.getUser2().getEmail().equals(requesterEmail));

        if (!isAdmin && !isParticipant) {
            throw new AccessDeniedException("Not authorized to view this conversation");
        }

        Pageable pageable = PageRequest.of(0, size);

        List<ConversationMessageProjection> messages =
                messageRepo.findConversationMessages(
                        conversationId,
                        beforeId,
                        pageable
                );

        String user1Email = conversation.getUser1() != null ? conversation.getUser1().getEmail() : null;
        String user2Email = conversation.getUser2() != null ? conversation.getUser2().getEmail() : null;

        return messages.stream()
                .map(m -> {
                    String sender = m.getSenderEmail();
                    String recipient = (sender != null && sender.equals(user1Email)) ? user2Email : user1Email;
                    return ConversationMessageResponse.builder()
                            .id(m.getMessageId())
                            .conversationId(m.getConversationId())
                            .content(m.getContent())
                            .sentAt(m.getSentAt())
                            .senderEmail(sender)
                            .senderName(m.getSenderName())
                            .senderProfileImageUrl(m.getSenderProfileImageUrl())
                            .recipientEmail(recipient)
                            .build();
                })
                .toList();
    }

    /**
     * CHAT-03 (P1-13 / EB-20): Enforce message ownership.
     * Throws AccessDeniedException on message absence or ownership mismatch.
     */
    @Transactional
    public ConversationMessageResponse editMessage(
            Long messageId,
            String senderEmail,
            EditMessageRequest request
    ) {

        Message msg = messageRepo.findById(messageId).orElse(null);
        if (msg == null) {
            throw new AccessDeniedException("Not allowed to edit this message");
        }

        if (msg.getSender() == null || !msg.getSender().getEmail().equals(senderEmail)) {
            throw new AccessDeniedException("Not allowed to edit this message");
        }

        msg.setContent(request.getContent());
        msg.setUpdated(true);

        return mapToResponse(msg);
    }

    /**
     * CHAT-03 / CHAT-04 (P1-13 / EB-20): Enforce message ownership and return
     * server-derived recipient email for event routing.
     */
    @Transactional
    public String deleteMessage(
            Long messageId,
            String senderEmail
    ) {
        Message msg = messageRepo.findById(messageId).orElse(null);
        if (msg == null) {
            throw new AccessDeniedException("Not allowed to delete this message");
        }

        if (msg.getSender() == null || !msg.getSender().getEmail().equals(senderEmail)) {
            throw new AccessDeniedException("Not allowed to delete this message");
        }

        Conversation c = msg.getConversation();
        String recipientEmail = null;
        if (c != null) {
            if (c.getUser1() != null && senderEmail.equals(c.getUser1().getEmail())) {
                recipientEmail = c.getUser2() != null ? c.getUser2().getEmail() : null;
            } else if (c.getUser2() != null && senderEmail.equals(c.getUser2().getEmail())) {
                recipientEmail = c.getUser1() != null ? c.getUser1().getEmail() : null;
            }
        }

        messageRepo.delete(msg);
        return recipientEmail;
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
                        .otherUserProfileImageUrl(c.getOtherUserProfileImageUrl())
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
