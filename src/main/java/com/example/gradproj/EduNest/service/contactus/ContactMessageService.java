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

import java.util.List;

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
                .build();

        contactMessageRepository.save(entity);
    }


    public List<ContactMessageResponseDto> getAllContactMessages() {
        return contactMessageRepository.findAll()
                .stream()
                .map(entity -> ContactMessageResponseDto.builder()
                        .id(entity.getId())
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
                .id(msg.getId())
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
                        .id(entity.getId())
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

        String template = emailService.getEmailTemplate("admin-reply.html");

        String html = template
                .replace("{{name}}", message.getName())
                .replace("{{reply}}", reply);

        emailService.sendEmail(
                message.getEmail(),
                "Re: Support Reply",
                html
        );

        message.setStatus(MessageStatus.COMPLETED);
        contactMessageRepository.save(message);
    }

    @Transactional
    public void sendNotificationToMessageSender(Long messageId, String title, String content) {

        ContactMessageEntity message = contactMessageRepository.findById(messageId)
                .orElseThrow(() -> new globalLogicEx("Message not found"));

        notificationService.sendToUserByEmail(
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




}
