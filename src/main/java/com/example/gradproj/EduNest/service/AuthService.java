package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.auth.RegisterRequest;
import com.example.gradproj.EduNest.entity.User;

public interface AuthService {
    User register(RegisterRequest registerRequest);
}
