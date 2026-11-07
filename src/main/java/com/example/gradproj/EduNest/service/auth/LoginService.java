package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.dto.auth.TokenPairResponseDto;

public interface LoginService {
    String loginProcess(LoginRequestDto loginRequestDto);
    TokenPairResponseDto loginWithRefreshToken(LoginRequestDto loginRequestDto);
    String getJwtRole(String jwt);
}
