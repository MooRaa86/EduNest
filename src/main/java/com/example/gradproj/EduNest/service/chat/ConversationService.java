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
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;
    private final com.example.gradproj.EduNest.service.chat.ChatAuthorization chatAuthorization;
    // [EB-44][P3-02] CHAT-05 — idempotent get-or-create guarded by the
    // uk_conversation_unordered_pair constraint; TransactionTemplate keeps the
    // insert in its own short transaction so a constraint-race never poisons
    // the caller's transaction.
    private final PlatformTransactionManager transactionManager;

    /**
     * [EB-44][P3-02] The recovery mechanism REQUIRES an independent transaction:
     * {@code PROPAGATION_REQUIRES_NEW} guarantees the conversation get-or-create
     * always runs in its own short transaction even if a future/websocket caller
     * wraps {@link #sendMessage} in an outer {@code @Transactional} scope. With
     * the default {@code REQUIRED} propagation the inner tx would JOIN the outer
     * one; a unique violation would then poison the outer tx and the recovery's
     * refetch/commit path would explode with {@code UnexpectedRollbackException}
     * at the outer boundary. REQUIRES_NEW confines the rollback to the failed
     * insert; the outer transaction stays healthy.
     */
    private TransactionTemplate requiresNewConversationTx;

    @PostConstruct
    void initConversationTemplate() {
        requiresNewConversationTx = new TransactionTemplate(transactionManager);
        requiresNewConversationTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ConversationMessageResponse mapToResponse(Message msg) {
        Conversation c = msg.getConversation();
        String recipientEmail = null;
        UUID recipientId = null;
        UUID senderId = msg.getSender() != null ? msg.getSender().getPublicId() : null;

        if (c != null && msg.getSender() != null) {
            String senderEmail = msg.getSender().getEmail();
            if (c.getUser1() != null && senderEmail.equals(c.getUser1().getEmail())) {
                recipientEmail = c.getUser2() != null ? c.getUser2().getEmail() : null;
                recipientId = c.getUser2() != null ? c.getUser2().getPublicId() : null;
            } else if (c.getUser2() != null && senderEmail.equals(c.getUser2().getEmail())) {
                recipientEmail = c.getUser1() != null ? c.getUser1().getEmail() : null;
                recipientId = c.getUser1() != null ? c.getUser1().getPublicId() : null;
            }
        }

        return ConversationMessageResponse.builder()
                .id(msg.getId())
                .conversationId(c != null ? c.getPublicId() : null)
                .senderId(senderId)
                .senderEmail(msg.getSender().getEmail())
                .senderName(msg.getSender().getFirstName() + " " + msg.getSender().getLastName())
                .senderProfileImageUrl(msg.getSender().getProfileImageUrl())
                .recipientId(recipientId)
                .recipientEmail(recipientEmail)
                .sentAt(msg.getSentAt())
                .content(msg.getContent())
                .build();
    }

    public ConversationMessageResponse sendMessage(
            String senderEmail,
            ConversationMessageRequest request
    ) {
        UserEntity receiver = resolveReceiver(request);
        chatAuthorization.validatePrivatePair(senderEmail, receiver.getEmail());

        UserEntity sender =
                userRepo.findByEmail(senderEmail).orElseThrow(
                        () -> new UsernameNotFoundException("Sender not found")
                );

        Conversation conversation;
        try {
            conversation = requiresNewConversationTx.execute(status ->
                    conversationRepo
                            .findBetweenUsers(senderEmail, receiver.getEmail())
                            .orElseGet(() -> createConversation(sender, receiver)));
        } catch (DataIntegrityViolationException dive) {
            // constraint race: refetch the winning conversation in a fresh transaction
            conversation = conversationRepo
                    .findBetweenUsers(senderEmail, receiver.getEmail())
                    .orElseThrow(() -> dive);
        }

        Message msg = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .sentAt(LocalDateTime.now())
                .build();

        Message savedMessage =  messageRepo.save(msg);

        return mapToResponse(savedMessage);
    }

    private UserEntity resolveReceiver(ConversationMessageRequest request) {
        if (request.getRecipientId() != null) {
            return userRepo.findByPublicId(request.getRecipientId())
                    .orElseThrow(() -> new UsernameNotFoundException("Receiver not found by ID: " + request.getRecipientId()));
        }
        String target = request.getRecipientEmail();
        if (target == null || target.isBlank()) {
            throw new globalLogicEx("Recipient identifier must not be blank");
        }
        return userRepo.findByEmail(target)
                .or(() -> {
                    try {
                        UUID uuid = UUID.fromString(target);
                        return userRepo.findByPublicId(uuid);
                    } catch (IllegalArgumentException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new UsernameNotFoundException("Receiver not found: " + target));
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
        UUID user1Id = conversation.getUser1() != null ? conversation.getUser1().getPublicId() : null;
        UUID user2Id = conversation.getUser2() != null ? conversation.getUser2().getPublicId() : null;

        return messages.stream()
                .map(m -> {
                    String sender = m.getSenderEmail();
                    String recipient = (sender != null && sender.equals(user1Email)) ? user2Email : user1Email;
                    UUID senderUid = (sender != null && sender.equals(user1Email)) ? user1Id : user2Id;
                    UUID recipientUid = (sender != null && sender.equals(user1Email)) ? user2Id : user1Id;
                    return ConversationMessageResponse.builder()
                            .id(m.getMessageId())
                            .conversationId(m.getConversationId())
                            .senderId(senderUid)
                            .recipientId(recipientUid)
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

    private Long resolveConversationInternalId(UUID publicId) {
        return conversationRepo.findIdByPublicId(publicId)
                .or(() -> conversationRepo.findById(publicId.getLeastSignificantBits()).map(Conversation::getId))
                .orElseThrow(() -> new globalLogicEx("Conversation not found"));
    }

    public List<ConversationMessageResponse> getConversationMessages(
            UUID conversationId,
            Long beforeId,
            int size,
            String requesterEmail,
            boolean isAdmin
    ) {
        Conversation conversation = conversationRepo.findByPublicId(conversationId)
                .or(() -> conversationRepo.findById(conversationId.getLeastSignificantBits()))
                .orElse(null);
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
        List<ConversationMessageProjection> messages = messageRepo.findConversationMessages(
                conversation.getId(),
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
                            .conversationId(conversationId)
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

}
