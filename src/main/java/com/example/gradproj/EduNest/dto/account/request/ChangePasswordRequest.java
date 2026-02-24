package com.example.gradproj.EduNest.dto.account.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @NotBlank(message = "oldPassword is required")
    @Size(min = 8, message = "oldPassword must be at least 8 characters long")
    private String oldPassword;
    @NotBlank(message = "newPassword is required")
    @Size(min = 8, message = "newPassword must be at least 8 characters long")
    private String newPassword;
    @NotBlank(message = "confirmPassword is required")
    @Size(min = 8, message = "confirmPassword must match newPassword")
    private String confirmPassword;
}