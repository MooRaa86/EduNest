package com.example.gradproj.EduNest.service.contactus;

import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.dto.contactus.ContactMessageResponseDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.enums.message.MessageStatus;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;


    public void saveContactMessage(ContactMessageRequestDto message) {
        ContactMessageEntity entity = ContactMessageEntity.builder()
                .name(message.getName())
                .email(message.getEmail())
                .phone(message.getPhone())
                .message(message.getMessage())
                .status(MessageStatus.PENDING)
                .build();

        contactMessageRepository.save(entity);
    }


    public List<ContactMessageResponseDto> getAllContactMessages() {
        return contactMessageRepository.findAll()
                .stream()
                .map(entity -> ContactMessageResponseDto.builder()
                        .id(entity.getPublicId())
                        .name(entity.getName())
                        .email(entity.getEmail())
                        .phone(entity.getPhone())
                        .message(entity.getMessage())
                        .status(entity.getStatus())
                        .build())
                .toList();
    }

    public ContactMessageResponseDto getSpecificMessage(Long msgId) {
        ContactMessageEntity msg = contactMessageRepository.findById(msgId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        return ContactMessageResponseDto.builder()
                .id(msg.getPublicId())
                .name(msg.getName())
                .phone(msg.getPhone())
                .email(msg.getEmail())
                .message(msg.getMessage())
                .status(msg.getStatus())
                .build();

    }

    public void updateMessageStatus(Long msgId, MessageStatus msgStatus) {
        ContactMessageEntity msg = contactMessageRepository.findById(msgId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));
        msg.setStatus(msgStatus);
        contactMessageRepository.save(msg);
    }

    public PageResponse<ContactMessageResponseDto> filterMessages(MessageStatus status, Pageable pageable) {
        Page<ContactMessageEntity> messages = contactMessageRepository.findByStatus(status, pageable);

        List<ContactMessageResponseDto> messageResponseDtos = messages.getContent().stream()
                .map(entity -> ContactMessageResponseDto.builder()
                        .id(entity.getPublicId())
                        .name(entity.getName())
                        .email(entity.getEmail())
                        .phone(entity.getPhone())
                        .message(entity.getMessage())
                        .status(entity.getStatus())
                        .build())
                .toList();
        return PageResponse.<ContactMessageResponseDto>builder()
                .content(messageResponseDtos)
                .page(messages.getNumber())
                .size(messages.getSize())
                .totalElements(messages.getTotalElements())
                .totalPages(messages.getTotalPages())
                .build();
    }


    @Transactional
    public void sendAdminReply(Long msgId, String reply) {
        ContactMessageEntity message = contactMessageRepository.findById(msgId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        if (message.getStatus() == MessageStatus.COMPLETED) {
            throw new globalLogicEx("Reply already sent for this message");
        }

        String template = emailService.getEmailTemplate("admin-reply.html");

        // P1-24 / EB-31 (SEL-15) — html-escape at merge time, the same pattern
        // as AdminBadgeService#sendBadgeAwardEmail. {{name}} is fully
        // attacker-controlled (PUBLIC /save-message, @NotBlank only) and the
        // reply is admin text; either way it must render INERT in the HTML
        // email (sendEmail uses setText(body, true) = HTML mode).
        String html = template
                .replace("{{name}}", HtmlUtils.htmlEscape(message.getName()))
                .replace("{{reply}}", HtmlUtils.htmlEscape(reply));

        emailService.sendEmail(
                message.getEmail(),
                "Re: Support Reply",
                html
        );

        message.setStatus(MessageStatus.COMPLETED);
        contactMessageRepository.save(message);
    }

    @Transactional
    public boolean sendNotificationToMessageSender(Long messageId, String title, String content) {

        ContactMessageEntity message = contactMessageRepository.findById(messageId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        return notificationService.sendToUserByEmailIfExists(
                message.getEmail(),
                title,
                content,
                NotificationType.SUPPORT
        );
    }

    @Transactional
    public void deleteMessage(Long msgId) {
        contactMessageRepository.deleteById(msgId);
    }

    @Transactional
    public void deleteAllMessages() {
        contactMessageRepository.deleteAll();
    }





    private Long resolveMessageInternalId(UUID publicId) {
        return contactMessageRepository.findIdByPublicId(publicId)
                .or(() -> contactMessageRepository.findById(publicId.getLeastSignificantBits()).map(ContactMessageEntity::getId))
                .orElseThrow(() -> new globalLogicEx("Message not found"));
    }

    public ContactMessageResponseDto getSpecificMessage(UUID msgId) {
        return getSpecificMessage(resolveMessageInternalId(msgId));
    }

    public void updateMessageStatus(UUID msgId, MessageStatus msgStatus) {
        updateMessageStatus(resolveMessageInternalId(msgId), msgStatus);
    }

    @Transactional
    public void sendAdminReply(UUID msgId, String reply) {
        sendAdminReply(resolveMessageInternalId(msgId), reply);
    }

    @Transactional
    public boolean sendNotificationToMessageSender(UUID messageId, String title, String content) {
        return sendNotificationToMessageSender(resolveMessageInternalId(messageId), title, content);
    }

    @Transactional
    public void deleteMessage(UUID msgId) {
        deleteMessage(resolveMessageInternalId(msgId));
    }

}
