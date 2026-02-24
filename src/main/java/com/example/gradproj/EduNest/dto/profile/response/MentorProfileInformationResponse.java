package com.example.gradproj.EduNest.dto.profile.response;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileInformationResponse {
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String jobTitle;
    private String bio;
    private double yearsOfExperience;
    private String linkedInLink;
    private String githubLink;
    private String profileImageUrl;

}
