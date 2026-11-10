package com.example.gradproj.EduNest.dto.profile;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import lombok.*;
import java.util.UUID;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentProjectProfileDTO {
    private UUID projectId;
    private UUID projectSubmissionId;

    private String projectTitle;
    private UUID mentorshipId;
    private String mentorshipTitle;

    private SubmissionStatus status;

    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;

    private String feedback;
    private String submissionLink;

    private Integer rawScore;
    private Integer finalScore;
}
