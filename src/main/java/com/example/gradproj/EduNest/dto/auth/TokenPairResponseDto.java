package com.example.gradproj.EduNest.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPairResponseDto {
    private String accessToken;
    private String refreshToken;
    private String role;
}
