package com.example.gradproj.EduNest.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class LoginRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format.")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
