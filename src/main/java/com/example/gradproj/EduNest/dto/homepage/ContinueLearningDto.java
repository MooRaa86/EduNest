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

    public static class ContinueLearningDtoBuilder {
        public ContinueLearningDtoBuilder mentorshipId(Long id) {
            this.mentorshipId = id != null ? new UUID(0L, id) : null;
            return this;
        }
        public ContinueLearningDtoBuilder mentorshipId(UUID id) {
            this.mentorshipId = id;
            return this;
        }
    }
}
