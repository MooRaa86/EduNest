package com.example.gradproj.EduNest.dto.weeks;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MentorshipWeeksWithContentsResponse {
    private Long mentorshipId;
    private String mentorshipTitle;
    private List<StudentWeekContentsResponse> weeks;
}
