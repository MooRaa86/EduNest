package com.example.gradproj.EduNest.dto.profile;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnrolledMentorshipProgressDto {
    private String imageUrl;
    private UUID mentorshipId;
    private String title;
    private Status status;

    private Integer totalPoints;

    private Long totalTasks;
    private Long submittedTasks;

    private Long totalQuizzes;
    private Long submittedQuizzes;
}
