package com.example.gradproj.EduNest.dto.profile.response.MentorProfileForStudent;

import lombok.*;
import java.util.UUID;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MentorProfileReviews {
    private UUID id;
    private String userName;
    private String userEmail;
    private long rating;
    private String feedBack;
    private String userProfileImageUrl;
    private UUID mentorshipId;
    private String mentorshipTitle;
    private LocalDateTime createdAt;
}
