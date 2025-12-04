package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.StudenRequestDto;
import org.springframework.stereotype.Service;

@Service
public interface RegisterationService{
    boolean registerStudent(StudenRequestDto studentDto);
    boolean registerMentor(MentorRequestDto mentorRequestDto);
}
