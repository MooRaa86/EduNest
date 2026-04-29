package com.example.gradproj.EduNest.dto.admin;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminMentorDetailResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String profileImageUrl;
        private Boolean enabled;
        private String bio;
        private String jobTitle;
        private Double yearsOfExperience;
        private Long totalSessions;
        private Long totalStudents;
        private Double averageRating;
        private Long totalBadges;
        private Long mentorshipCount;
        private List<SocialMediaItem> socialMedia;
        private List<BadgeSummary> badges;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BadgeSummary {
            private Long id;
            private String title;
            private String category;
            private int points;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SocialMediaItem {
            private String name;
            private String url;
    }
}
