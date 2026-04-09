package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentInLeaderboardDto {
    private String studentFullName;
    private String studentEmail;
    private EducationalLevel studentLevel;
    private String studentProfileImageUrl;
    private Integer totalPoints;
    private long rank;
    private String lastBadgeTitle;
    private BadgeCategory lastBadgeCategory;
}
