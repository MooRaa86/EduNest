package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.StudentRequestDto;
import org.springframework.stereotype.Service;

public interface RegistrationService {
    void registerStudent(StudentRequestDto studentDto);
    void registerMentor(MentorRequestDto mentorRequestDto);
}
