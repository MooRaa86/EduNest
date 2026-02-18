package com.example.gradproj.EduNest.dto.profile;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentProjectProfileDTO {
    private Long projectSubmissionId;

    private String projectTitle;
    private String mentorshipTitle;

    private SubmissionStatus status;

    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;

    private String feedback;

    private Integer rawScore;
    private Integer finalScore;
}
