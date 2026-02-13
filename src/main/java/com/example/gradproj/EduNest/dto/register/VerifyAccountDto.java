package com.example.gradproj.EduNest.dto.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class VerifyAccountDto {

    @NotBlank(message = "Email is required")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)\\.(com|net|org|edu|ac\\.[a-z]{2,3})$",
            message = "Invalid email format.")
    private String email;

    @NotBlank
    @Size(min = 6, max = 6, message = "OTP must be 6 characters long")
    @Pattern(regexp = "\\d{6}", message = "OTP must contain only digits")
    private String otp;

}
