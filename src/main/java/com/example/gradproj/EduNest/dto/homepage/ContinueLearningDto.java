package com.example.gradproj.EduNest.dto.homepage;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContinueLearningDto {
    private Long mentorshipId;
    private String title;
    private String coverImageUrl;
    private String mentorName;
    private Status status;
    private Integer progressPercentage;
    private Integer totalWeeks;
    private Integer completedItems;
    private Integer totalItems;
}
