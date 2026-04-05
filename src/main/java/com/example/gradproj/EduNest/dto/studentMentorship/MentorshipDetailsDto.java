package com.example.gradproj.EduNest.dto.studentMentorship;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorshipDetailsDto {
    private BeforeEnrollData beforeEnroll;
    private AfterEnrollData afterEnroll;
}
