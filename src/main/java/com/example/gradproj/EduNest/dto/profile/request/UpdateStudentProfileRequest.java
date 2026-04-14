package com.example.gradproj.EduNest.dto.profile.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateStudentProfileRequest {
    private String firstName;
    private String lastName;
    private String bio;
    private String jobTitle;
    private List<SocialMediaRequest> socialMediaLinks;
}
