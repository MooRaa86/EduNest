package com.example.gradproj.EduNest.dto.homepage;

import com.example.gradproj.EduNest.repository.mentorShip.projections.RecommendedMentorshipProjection;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentHomePageResponse {
    private List<UpcomingItemDto> upcomingItems;
    private List<ContinueLearningDto> continueLearning;
    private List<RecommendedMentorshipProjection> recommendedMentorships;
}
