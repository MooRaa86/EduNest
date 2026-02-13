package com.example.gradproj.EduNest.service.contactus;

import com.example.gradproj.EduNest.dto.contactus.ContactMessageRequestDto;

import java.util.List;

public interface ContactMessageService {
    void saveContactMessage(ContactMessageRequestDto contactMessageRequestDto);
    List<ContactMessageRequestDto> getAllContactMessages();
}
