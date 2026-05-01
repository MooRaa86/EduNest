package com.example.gradproj.EduNest.dto.admin;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminStudentDetailResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String profileImageUrl;
        private Boolean enabled;
        private String educationalLevel;
        private String jobTitle;
        private String bio;
        private Long totalEnrollments;
        private Long totalCompletedMentorships;
        private Long totalBadgesEarned;
        private List<SocialMediaItem> socialMedia;
        private List<AdminBadgeSummary> adminBadges;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SocialMediaItem {
            private String name;
            private String url;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdminBadgeSummary {
            private Long id;
            private String name;
            private String description;
            private String type;
    }
}
