package com.example.gradproj.EduNest.dto.profile;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileStudentInformationForMentorResponse {
    private String imageUrl;
    private String name;
    private String email;
    private String address;
    private String facebookLink;
    private String linkedInLink;
    private String githubLink;
    private Long activeMentorships;
    private Long completedMentorships;
    private Integer totalPoints;

}
