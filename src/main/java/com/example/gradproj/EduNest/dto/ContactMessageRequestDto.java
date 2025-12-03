package com.example.gradproj.EduNest.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ContactMessageRequestDto {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)\\.(com|net|org|edu|ac\\.[a-z]{2,3})$",
            message = "Invalid email format.")
    private String email;

    @Pattern(regexp = "^01[0-9]{9}$", message = "Invalid phone number")
    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Message is required")
    private String message;
}