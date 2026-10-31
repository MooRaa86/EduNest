package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileReviews {
    private Long id;
    private String userName;
    private String userEmail;
    private long rating;
    private String feedBack;
    private String userProfileImageUrl;
    private Long mentorshipId;
    private String mentorshipTitle;
    private LocalDateTime createdAt;
}
