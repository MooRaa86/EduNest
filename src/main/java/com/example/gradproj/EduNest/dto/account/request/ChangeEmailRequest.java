package com.example.gradproj.EduNest.dto.account.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEmailRequest {

    @Email
    @NotBlank
    private String newEmail;

    @NotBlank
    private String password;
}
