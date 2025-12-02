package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.ContactMessageRequestDto;

import java.util.List;

public interface ContactMessageService {
    void saveContactMessage(ContactMessageRequestDto contactMessageRequestDto);
    List<ContactMessageRequestDto> getAllContactMessages();
}
