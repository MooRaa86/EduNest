package com.example.gradproj.EduNest.dto.studentAchievement;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectAchievementResponse {
    private UUID projectId;
    private UUID submissionId;
    private String projectTitle;
    private UUID mentorshipId;
    private String mentorshipTitle;
    private String mentorFullName;
    private SubmissionStatus submissionStatus;
    private String fileUrl;
    private String uploadedFilePath;
    private String feedback;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
