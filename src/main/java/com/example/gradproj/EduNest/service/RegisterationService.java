package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.RegisterRequestDto;
import org.springframework.stereotype.Service;

@Service
public interface RegisterationService{
    boolean registerUser(RegisterRequestDto registerRequestDto);
}
