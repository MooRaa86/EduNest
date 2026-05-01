package com.example.gradproj.EduNest.service.contactus;

import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.dto.contactus.ContactMessageResponseDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quiz.response.QuizResponseDTO;
import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.message.MessageStatus;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    @Override
    public void saveContactMessage(ContactMessageRequestDto dto) {
        ContactMessageEntity entity = ContactMessageEntity.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .message(dto.getMessage())
                .build();

        contactMessageRepository.save(entity);
    }

    @Override
    public List<ContactMessageResponseDto> getAllContactMessages() {
        return contactMessageRepository.findAll()
                .stream()
                .map(entity -> ContactMessageResponseDto.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .email(entity.getEmail())
                        .phone(entity.getPhone())
                        .message(entity.getMessage())
                        .build())
                .toList();
    }

    public ContactMessageResponseDto getSpecificMessage(Long msgId) {
        ContactMessageEntity msg = contactMessageRepository.findById(msgId).get();
        return ContactMessageResponseDto.builder()
                .name(msg.getName())
                .phone(msg.getPhone())
                .email(msg.getEmail())
                .message(msg.getMessage())
                .build();

    }

    public void updateMessageStatus(Long msgId, MessageStatus msgStatus) {
        ContactMessageEntity msg = contactMessageRepository.findById(msgId).get();
        msg.setStatus(msgStatus);
        contactMessageRepository.save(msg);
    }

    public PageResponse<ContactMessageResponseDto> filterMessages( MessageStatus status,  Pageable pageable) {
        Page<ContactMessageEntity> messages = contactMessageRepository.findByStatus(status,pageable);

        List<ContactMessageResponseDto> messageResponseDtos = messages.getContent().stream()
                .map(entity -> ContactMessageResponseDto.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .email(entity.getEmail())
                        .phone(entity.getPhone())
                        .message(entity.getMessage())
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



}
