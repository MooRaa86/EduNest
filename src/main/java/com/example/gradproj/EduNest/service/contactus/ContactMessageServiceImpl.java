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
        ContactMessageEntity entity = new ContactMessageEntity();
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setMessage(dto.getMessage());
        entity.setCreatedBy(SYSTEM);
        entity.setUpdatedBy(SYSTEM);

        contactMessageRepository.save(entity);
    }

    @Override
    public List<ContactMessageRequestDto> getAllContactMessages() {
        List<ContactMessageEntity> entities = contactMessageRepository.findAll();

        List<ContactMessageRequestDto> messages = new ArrayList<>();
        for (ContactMessageEntity entity : entities) {
            ContactMessageRequestDto messageDto = new ContactMessageRequestDto();
            messageDto.setName(entity.getName());
            messageDto.setEmail(entity.getEmail());
            messageDto.setPhone(entity.getPhone());
            messageDto.setMessage(entity.getMessage());


            messages.add(messageDto);
        }

        return messages;
    }
}
