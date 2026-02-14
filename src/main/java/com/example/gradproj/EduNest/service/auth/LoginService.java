package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;

public interface LoginService {
    String loginProcess(LoginRequestDto loginRequestDto);
    String getJwtRole(String jwt);
}
