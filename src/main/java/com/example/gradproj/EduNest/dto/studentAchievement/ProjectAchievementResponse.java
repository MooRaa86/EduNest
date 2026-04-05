package com.example.gradproj.EduNest.dto.studentAchievement;

import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectAchievementResponse {
    private Long id;
    private String projectTitle;
    private Long mentorshipId;
    private String mentorshipTitle;
    private String mentorFullName;
    private SubmissionStatus submissionStatus;
    private String fileUrl;
    private String uploadedFilePath;
    private String feedback;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
}
