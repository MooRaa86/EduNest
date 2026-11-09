package com.example.gradproj.EduNest.dto.mylearning;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnrolledMentorshipDto {
    private UUID mentorshipId;
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

    public static class EnrolledMentorshipDtoBuilder {
        public EnrolledMentorshipDtoBuilder mentorshipId(Long id) {
            this.mentorshipId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public EnrolledMentorshipDtoBuilder mentorshipId(UUID id) {
            this.mentorshipId = id;
            return this;
        }
    }
}
