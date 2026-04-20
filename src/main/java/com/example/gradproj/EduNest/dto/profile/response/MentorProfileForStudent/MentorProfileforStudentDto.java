package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileforStudentDto {
    private String profileImageUrl;
    private String mentorFirstName;
    private String mentorLastName;
    private Long totalLearners;
    private Long totalReviews;
    private Double avgReviewRate;
    private String bio;
    private String mentorEmail;
    private List<SocialMediaLinksDto> socialMediaLinks;
}
