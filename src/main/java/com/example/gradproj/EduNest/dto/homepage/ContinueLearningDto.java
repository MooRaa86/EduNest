package com.example.gradproj.EduNest.dto.homepage;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContinueLearningDto {
    private UUID mentorshipId;
    private String title;
    private String coverImageUrl;
    private String mentorName;
    private Integer progressPercentage;
    private Integer completedItems;
    private Integer totalItems;
}
