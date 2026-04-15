package com.example.gradproj.EduNest.dto.profile.response;

import com.example.gradproj.EduNest.dto.certificate.CertificateResponse;
<<<<<<< HEAD
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
=======
>>>>>>> a730537f49ec36c9895dfea6d0b7aa045d3a1e3d
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
<<<<<<< HEAD
    private PageResponse<CertificateResponse> certificates;
=======
    private List<CertificateResponse>certificates;
>>>>>>> a730537f49ec36c9895dfea6d0b7aa045d3a1e3d
    private List<String> skills;
}
