package com.example.gradproj.EduNest.dto.tasks.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskWithSubmissionForStudentResponse {

    private UUID taskId;
    private String taskTitle;
    private Integer points;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;
    private String description;
    private String attachmentUrl;
    private String uploadedAttachmentPath;
    private Integer estimatedMinutes;

    private UUID submissionId;
    private String submissionUrl;
    private String uploadedSubmissionPath;
    private Integer score;
    private Integer totalPoints;
    private SubmissionStatus submissionStatus;
    private String feedback;
    private String mentorName;
    private String mentorPhoto;
}
