package com.example.gradproj.EduNest.dto.mylearning;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnrolledMentorshipDto {
    private Long mentorshipId;
    private String title;
    private String subtitle;
    private String category;
    private String difficultyLevel;
    private String coverImageUrl;
    private Integer totalPoints;
    private Integer progressPercentage;

    private Long totalTasks;
    private Long submittedTasks;
    private Long totalQuizzes;
    private Long submittedQuizzes;
    private Long totalProjects;
    private Long submittedProjects;
    private Long totalLectures;
    private String status;
}
