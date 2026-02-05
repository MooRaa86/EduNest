package com.example.gradproj.EduNest.service.contactUs;

import com.example.gradproj.EduNest.dto.contactUs.ContactMessageRequestDto;

import java.util.List;

public interface ContactMessageService {
    void saveContactMessage(ContactMessageRequestDto contactMessageRequestDto);
    List<ContactMessageRequestDto> getAllContactMessages();
}
