package com.example.gradproj.EduNest.dto.tasks.response;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskWithSubmissionForStudentResponse {

    private Long taskId;
    private String taskTitle;
    private Integer points;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;
    private String description;
    private String attachmentUrl;
    private String uploadedAttachmentPath;
    private Integer estimatedMinutes;

    private String submissionUrl;
    private Integer score;
    private Integer totalPoints;
    private SubmissionStatus submissionStatus;
    private String feedback;
    private String mentorName;
    private String mentorPhoto;
}
