package com.example.gradproj.EduNest.service.contactus;

import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;
import com.example.gradproj.EduNest.entity.contactus.ContactMessageEntity;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.example.gradproj.EduNest.utils.SystemUtils.SYSTEM;

@Service
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageServiceImpl(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    @Override
    public void saveContactMessage(ContactMessageRequestDto dto) {
        ContactMessageEntity entity = ContactMessageEntity.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .message(dto.getMessage())
                .createdBy(SYSTEM)
                .updatedBy(SYSTEM)
                .build();


        contactMessageRepository.save(entity);
    }

    @Override
    public List<ContactMessageRequestDto> getAllContactMessages() {
        List<ContactMessageEntity> entities = contactMessageRepository.findAll();

        List<ContactMessageRequestDto> messages = new ArrayList<>();
        for (ContactMessageEntity entity : entities) {
            ContactMessageRequestDto messageDto = ContactMessageRequestDto.builder()
                    .name(entity.getName())
                    .email(entity.getEmail())
                    .phone(entity.getPhone())
                    .message(entity.getMessage())
                    .build();



            messages.add(messageDto);
        }

        return messages;
    }
}
