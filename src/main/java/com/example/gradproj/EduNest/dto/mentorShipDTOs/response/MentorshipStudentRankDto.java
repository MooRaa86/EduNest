package com.example.gradproj.EduNest.dto.mentorShipDTOs.response;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorshipStudentRankDto {
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private Integer totalPoints;
    private Integer rank;
}
