package com.example.gradproj.EduNest.dto.homepage;

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
    private Integer progressPercentage;
    private Integer completedItems;
    private Integer totalItems;
}
