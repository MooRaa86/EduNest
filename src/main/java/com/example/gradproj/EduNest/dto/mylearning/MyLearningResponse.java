package com.example.gradproj.EduNest.dto.mylearning;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyLearningResponse {
    private long completedMentorships;
    private double averageProgress;
    private long totalPoints;
    private PageResponse<EnrolledMentorshipDto> mentorships;
}
