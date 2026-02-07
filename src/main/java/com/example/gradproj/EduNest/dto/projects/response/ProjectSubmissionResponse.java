package com.example.gradproj.EduNest.dto.projects.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectSubmissionResponse {
    private Long submissionId;
    private Long taskId;
    private Long studentId;

    private String fileUrl;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // for unify the date formate
    private LocalDateTime submittedAt;

    private Boolean isLate;
    private SubmissionStatus status;

    private Integer rawScore;
    private Integer finalScore;
    private String feedback;
}
