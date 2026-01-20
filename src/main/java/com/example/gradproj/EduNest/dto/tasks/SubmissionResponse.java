package com.example.gradproj.EduNest.dto.tasks;

import lombok.*;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SubmissionResponse {
    private Long id;
    private Long taskId;
    private Long studentId;
    private Integer attemptNo;
    private String fileUrl;
    private String status;
    private Boolean isLate;
    private Integer rawScore;
    private Integer finalScore;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
}
