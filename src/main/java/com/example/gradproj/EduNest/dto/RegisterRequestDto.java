package com.example.gradproj.EduNest.dto;

import com.example.gradproj.EduNest.annotation.FieldsValueMatch;
import com.example.gradproj.EduNest.annotation.roleValidator;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@FieldsValueMatch(field = "password",
        fieldMatch = "confirmPassword",
        message = "Passwords do not match")
public class RegisterRequestDto {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 20, message = "First name must be between 2 and 20 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 20, message = "Last name must be between 2 and 20 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+)\\.(com|net|org|edu|ac\\.[a-z]{2,3})$",
            message = "Invalid email format.")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Confirm password is required")
    @Size(min = 8, message = "Confirm Password must be at least 8 characters long")
    private String confirmPassword;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^01[0-9]{9}$", message = "Invalid phone number")
    private String phoneNumber;

//    @NotNull(message =  "Role Id is required")
//    @roleValidator
//    private Long roleId;
}
