package com.example.gradproj.EduNest.dto.profile.request;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateMentorProfileRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String bio;
    private String jobTitle;
    private Double yearsOfExperience;
    private String linkedInLink;
    private String githubLink;
}