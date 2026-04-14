package com.example.gradproj.EduNest.dto.profile.response;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
import com.example.gradproj.EduNest.dto.studentAchievement.BadgeAchievementResponse;
import com.example.gradproj.EduNest.dto.profile.StudentProjectProfileDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentProfileInformationResponse {
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String jobTitle;
    private String bio;
    private String profileImageUrl;
    private String githubLink;
    private String linkedInLink;
    private List<BadgeAchievementResponse> badges;
    private List<StudentProjectProfileDTO> projects;
    private List<CertificateResponse>certificates;
    private List<String> skills;
}
