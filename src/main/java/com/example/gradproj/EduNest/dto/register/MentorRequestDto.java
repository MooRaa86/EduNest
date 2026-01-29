package com.example.gradproj.EduNest.dto.register;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
@Builder
public class MentorRequestDto extends RegisterRequestDto{

    @Size(min = 2, max = 50, message = "Invalid , Job title must be between 2 and 50 characters")
    private String jobTitle; // enum -> static

    @Size(max = 400)
    private String bio;

    @Pattern(
            regexp = "^(https?://)?(www\\.)?linkedin\\.com/.*$",
            message = "Invalid LinkedIn URL"
    )
    private String linkedInUrl;

    @Pattern(
            regexp = "^(https?://)?(www\\.)?github\\.com/.*$",
            message = "Invalid GitHub URL"
    )
    private String githubUrl;

    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 20, message = "Years of experience cannot exceed 20")
    private double yearsOfExperience;
}
