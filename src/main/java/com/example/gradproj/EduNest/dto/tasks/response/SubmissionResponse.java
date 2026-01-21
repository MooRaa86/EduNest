package com.example.gradproj.EduNest.dto.tasks.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import lombok.*;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SubmissionResponse {

    private Long submissionId;
    private Long taskId;
    private Long studentId;

    private String fileUrl;
    private LocalDateTime submittedAt;

    private Boolean isLate;
    private SubmissionStatus status;

    private Integer rawScore;
    private Integer finalScore;
    private String feedback;
}

