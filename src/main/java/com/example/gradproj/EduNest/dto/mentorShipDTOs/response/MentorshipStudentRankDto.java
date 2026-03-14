package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorshipStudentRankDto {
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private Integer totalPoints;
    private Integer rank;
}
